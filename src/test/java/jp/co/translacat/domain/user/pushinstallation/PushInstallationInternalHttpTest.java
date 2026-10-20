package jp.co.translacat.domain.user.pushinstallation;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.infrastructure.chat.core.ChatCoreIdentitySecurity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static jp.co.translacat.domain.user.pushinstallation.PushInstallationDtos.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PushInstallationInternalHttpTest {
    private static final byte[] KEY = "synthetic-internal-push-key-not-production-2026".getBytes(StandardCharsets.UTF_8);
    private static final String QUERY = "/internal/v1/chat/push-targets/query";
    private static final String INVALIDATE = "/internal/v1/chat/push-installations/invalidate";
    private static final String PROJECT = "11111111-1111-4111-8111-111111111111";
    private static final String ID = "22222222-2222-4222-8222-222222222222";
    private static final String BINDING = "33333333-3333-4333-8333-333333333333";
    private static AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private PushInstallationService service;

    @BeforeAll static void start() {
        // 준비: 실제 HMAC verifier와 filter chain만 사용하며 DB/remote/계정 설정은 로드하지 않는다.
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic-push-internal", Map.of(
                "chat.core.identity.environment", "Development", "chat.core.identity.issuer", "synthetic-chat",
                "chat.core.identity.audience", "synthetic-be", "chat.core.identity.service", "synthetic-worker",
                "chat.core.identity.secret-base64", Base64.getEncoder().encodeToString(KEY))));
        context.register(Web.class); context.refresh();
    }
    @AfterAll static void stop() { if (context != null) context.close(); }
    @BeforeEach void prepare() {
        service = context.getBean(PushInstallationService.class); reset(service);
        when(service.targets(anyList(), anyString(), anyString())).thenReturn(new Targets(List.of(new Target(
                11, ID, BINDING, 1, 1, "ExpoPushToken[SyntheticInternalOnly]", "android"))));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void exact_scopes_allow_only_the_two_intended_operations() throws Exception {
        // 실행 / 검증
        mvc.perform(post(QUERY).servletPath(QUERY).header("Authorization", "Bearer " + token("chat:push-targets:read", "chat-core-service"))
                .contentType("application/json").content(query()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.targets[0].installationId").value(ID)).andExpect(jsonPath("$.body").doesNotExist())
                .andExpect(jsonPath("$.targets[0].sid").doesNotExist()).andExpect(jsonPath("$.targets[0].installationProof").doesNotExist());
        mvc.perform(post(INVALIDATE).servletPath(INVALIDATE).header("Authorization", "Bearer " + token("chat:push-installations:invalidate", "chat-core-service"))
                .contentType("application/json").content(invalidate())).andExpect(status().isNoContent());
        verify(service).targets(List.of(11L), "development", PROJECT);
        verify(service).invalidate(new Invalidate(ID, BINDING, 1, 1));
    }

    @Test void wrong_scope_identity_token_user_token_missing_auth_and_method_do_not_reach_service() throws Exception {
        // 준비 / 실행 / 검증
        for (String rejected : List.of(token("chat:accounts:read", "chat-core-service"),
                token("chat:push-installations:invalidate", "chat-core-service"), token("chat:identity:read", "chat-identity"),
                token("chat:push-targets:read", "access"))) {
            mvc.perform(post(QUERY).servletPath(QUERY).header("Authorization", "Bearer " + rejected).contentType("application/json").content(query()))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post(QUERY).servletPath(QUERY).contentType("application/json").content(query())).andExpect(status().isUnauthorized());
        mvc.perform(get(QUERY).servletPath(QUERY).header("Authorization", "Bearer " + token("chat:push-targets:read", "chat-core-service")))
                .andExpect(status().is4xxClientError());
        mvc.perform(post(QUERY + "/extra").servletPath(QUERY + "/extra").header("Authorization", "Bearer " + token("chat:push-targets:read", "chat-core-service"))
                .contentType("application/json").content(query())).andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test void malformed_and_trailing_internal_json_are_rejected_with_fixed_errors() throws Exception {
        // 준비 / 실행 / 검증
        for (String value : List.of(query() + " {}", query().replace("[11]", "[\"11\"]"), query().replace("{", "{\"userId\":11,"))) {
            mvc.perform(post(QUERY).servletPath(QUERY).header("Authorization", "Bearer " + token("chat:push-targets:read", "chat-core-service"))
                    .contentType("application/json").content(value))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body.errorCode").value("PUSH_INVALID_INPUT"));
        }
        verifyNoInteractions(service);
    }

    private static String query() { return "{\"userIds\":[11],\"appEnvironment\":\"development\",\"projectId\":\"" + PROJECT + "\"}"; }
    private static String invalidate() { return "{\"installationId\":\"" + ID + "\",\"accountBindingId\":\"" + BINDING + "\",\"generation\":1,\"tokenVersion\":1}"; }
    private static String token(String scope, String purpose) {
        Instant now = Instant.now();
        return Jwts.builder().issuer("synthetic-chat").audience().add("synthetic-be").and().subject("synthetic-worker")
                .claim("service", "synthetic-worker").claim("tokenUse", purpose).claim("environment", "Development")
                .claim("scopes", List.of(scope)).issuedAt(Date.from(now.minusSeconds(1))).expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(KEY)).compact();
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    @Import({ChatCoreIdentitySecurity.class, PushInstallationInternalController.class, PushInstallationAdvice.class})
    static class Web {
        @Bean PushInstallationService pushInstallationService() { return mock(PushInstallationService.class); }
    }
}
