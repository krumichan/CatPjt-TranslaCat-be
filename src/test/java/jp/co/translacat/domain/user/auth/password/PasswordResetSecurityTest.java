package jp.co.translacat.domain.user.auth.password;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jp.co.translacat.domain.user.auth.AuthExceptionAdvice;
import jp.co.translacat.domain.user.auth.email.EmailChallengeResponse;
import jp.co.translacat.domain.user.auth.email.EmailVerificationResponse;
import jp.co.translacat.domain.user.auth.email.EmailVerificationService;
import jp.co.translacat.global.config.SecurityConfig;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.JwtFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PasswordResetController.class, properties = {
        "cors.allowed-origin=http://localhost:3000",
        "jwt.token.secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.token.expired.access=300000", "jwt.token.expired.refresh=604800000"
})
@Import({SecurityConfig.class, JwtFilter.class, JWTService.class, ApiLoggingFilter.class, AuthExceptionAdvice.class})
@ContextConfiguration(classes = PasswordResetController.class)
class PasswordResetSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean EmailVerificationService verification;
    @MockitoBean PasswordResetService reset;

    @Test
    void onlyExactPostEndpointsAreAnonymous() throws Exception {
        // 실행 및 검증: GET이나 임의 하위 경로는 익명 허용에 포함하지 않는다.
        for (String path : new String[]{"challenges", "verify", "complete"}) {
            mvc.perform(get("/api/v1/auth/password-reset/" + path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/password-reset/unknown").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(verification, reset);
    }

    @Test
    void anonymousRecoveryKeepsEnvelopeAndNeverLogsEmailCodeProofOrPassword() throws Exception {
        // 준비: HTTP 경계의 실제 로깅 필터를 관찰하며 외부 발송은 대역으로 둔다.
        String email = "synthetic-private@example.invalid";
        String id = "610ba52e-7248-4b11-8010-07ac803e31ca";
        String code = "928473";
        String proof = "p".repeat(43);
        String password = "synthetic-private-password";
        when(verification.sendPasswordReset(email)).thenReturn(new EmailChallengeResponse(id, 600, 60));
        when(verification.verifyPasswordReset(id, code)).thenReturn(new EmailVerificationResponse(proof, 600));
        Logger logger = (Logger) LoggerFactory.getLogger(ApiLoggingFilter.class);
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try {
            // 실행: 공개 요청에서도 비밀 응답의 캐시와 로그 경계를 유지한다.
            mvc.perform(post("/api/v1/auth/password-reset/challenges").contentType("application/json")
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.body.challengeId").value(id));
            mvc.perform(post("/api/v1/auth/password-reset/verify").contentType("application/json")
                            .content("{\"challengeId\":\"" + id + "\",\"code\":\"" + code + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.body.verificationToken").value(proof));
            mvc.perform(post("/api/v1/auth/password-reset/complete").contentType("application/json")
                            .content("{\"email\":\"" + email + "\",\"verificationToken\":\"" + proof + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.body.success").value(true));

            // 검증
            verify(reset).complete(email, proof, password);
            assertThat(captured.list).isNotEmpty();
            String logs = captured.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
            assertThat(logs).doesNotContain(email, id, code, proof, password);
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }
}
