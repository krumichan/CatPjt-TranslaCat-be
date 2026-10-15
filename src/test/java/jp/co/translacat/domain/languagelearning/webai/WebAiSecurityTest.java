package jp.co.translacat.domain.languagelearning.webai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.global.config.SecurityConfig;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.JwtFilter;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningWebAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {WebAiLearningController.class, WebAiExternalController.class}, properties = {
        "cors.allowed-origin=http://localhost:3000",
        "jwt.token.secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.token.expired.access=86400000",
        "jwt.token.expired.refresh=604800000"
})
@Import({SecurityConfig.class, JwtFilter.class, JWTService.class, ApiLoggingFilter.class,
        WebAiExceptionAdvice.class, WebAiSecurityTest.Support.class})
class WebAiSecurityTest {
    private static final String ROOT = "/api/v1/language-learning/web-ai";
    private static final String CAPABILITY = "tcwa_synthetic_plan_capability_123456789";
    @Autowired
    private MockMvc mvc;
    @Autowired
    private LanguageLearningWebAiClient client;
    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void resetClient() {
        reset(client);
    }

    @Test
    void anonymousAndCapabilityCannotManagePlans() throws Exception {
        // 준비 / 실행
        mvc.perform(get(ROOT + "/plans")).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/plans").header("Authorization", "Bearer " + CAPABILITY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT + "/plans/plan-1/connection").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());

        // 검증
        verifyNoInteractions(client);
    }

    @Test
    void externalCapabilityDoesNotAuthenticateArbitraryExternalOrWrongMethodRoutes() throws Exception {
        // 준비 / 실행
        mvc.perform(get(ROOT + "/external/prompt").header("Authorization", "Bearer " + CAPABILITY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT + "/external/other").header("Authorization", "Bearer " + CAPABILITY)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());

        // 검증
        verifyNoInteractions(client);
    }

    @Test
    void externalPromptUsesCapabilityWithoutAccountJwt() throws Exception {
        // 준비
        when(client.externalPrompt(eq(CAPABILITY), any())).thenReturn(json.readTree("{\"id\":\"run-1\"}"));

        // 실행
        mvc.perform(post(ROOT + "/external/prompt").header("Authorization", "Bearer " + CAPABILITY)
                        .contentType("application/json").content("{\"executionKey\":\"2026-10-15T18:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body.id").value("run-1"))
                .andExpect(header().string("Cache-Control", "no-store"));

        // 검증
        verify(client).externalPrompt(eq(CAPABILITY), any());
        verifyNoMoreInteractions(client);
    }

    @Test
    void missingOrMalformedCapabilityIsUnauthorizedWithoutNetworkCall() throws Exception {
        // 준비 / 실행
        mvc.perform(post(ROOT + "/external/results").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.body.errorCode").value("WEB_AI_TOKEN_INVALID"));
        mvc.perform(post(ROOT + "/external/prompt").header("Authorization", "Bearer too-short")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());

        // 검증
        verifyNoInteractions(client);
    }

    @Test
    void revokedCapabilityKeeps401WhileInternalAuthFailureBecomes502() throws Exception {
        // 준비
        when(client.externalPrompt(eq(CAPABILITY), any()))
                .thenThrow(new LanguageLearningServiceException(HttpStatus.UNAUTHORIZED,
                        "WEB_AI_TOKEN_REVOKED", "연결이 해제되었습니다."))
                .thenThrow(new LanguageLearningServiceException(HttpStatus.UNAUTHORIZED,
                        "INTERNAL_AUTH_INVALID", "내부 인증 실패"));

        // 실행 / 검증
        mvc.perform(post(ROOT + "/external/prompt").header("Authorization", "Bearer " + CAPABILITY)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.body.errorCode").value("WEB_AI_TOKEN_REVOKED"));
        mvc.perform(post(ROOT + "/external/prompt").header("Authorization", "Bearer " + CAPABILITY)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.body.errorCode").value("LL_INTERNAL_AUTH_FAILED"));
    }

    @Test
    void accountOwnerComesFromPrincipalAndCannotBeReplacedInQuery() throws Exception {
        // 준비
        var principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(123L);
        when(client.plans(123L)).thenReturn(json.readTree("{\"plans\":[]}"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        // 실행
        mvc.perform(get(ROOT + "/plans").param("userId", "999").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body.plans").isArray());

        // 검증
        verify(client).plans(123L);
        verifyNoMoreInteractions(client);
    }

    @Test
    void disabledLearnerDenialIsNotAnInternalAuthenticationFailure() throws Exception {
        // 준비
        when(client.externalPrompt(eq(CAPABILITY), any()))
                .thenThrow(new LanguageLearningServiceException(HttpStatus.FORBIDDEN,
                        "LEARNER_UNAVAILABLE", "이 학습자는 현재 이용할 수 없습니다."));

        // 실행 / 검증
        mvc.perform(post(ROOT + "/external/prompt").header("Authorization", "Bearer " + CAPABILITY)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.body.errorCode").value("LEARNER_UNAVAILABLE"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Support {
        @Bean
        LanguageLearningWebAiClient languageLearningWebAiClient() {
            return mock(LanguageLearningWebAiClient.class);
        }

        @Bean
        WebAiConnectionPresenter webAiConnectionPresenter() {
            return new WebAiConnectionPresenter("https://api.example.test");
        }
    }
}
