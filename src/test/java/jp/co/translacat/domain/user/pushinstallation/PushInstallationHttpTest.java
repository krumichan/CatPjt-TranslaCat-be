package jp.co.translacat.domain.user.pushinstallation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.auth.session.AuthSessionRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static jp.co.translacat.domain.user.pushinstallation.PushInstallationDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PushInstallationHttpTest {
    private static final String ID = "22222222-2222-4222-8222-222222222222";
    private static final String SID = "33333333-3333-4333-8333-333333333333";
    private static final String PROJECT = "11111111-1111-4111-8111-111111111111";
    private static final String PROOF = "a".repeat(64);
    private static final String TOKEN = "ExpoPushToken[SYNTHETIC_NEVER_REFLECT]";
    private static final String BASE = "/api/v1/users/me/push-installations/";
    private static final String KEY = Base64.getEncoder().encodeToString("synthetic-push-http-key-that-is-not-a-real-secret-2026".getBytes(StandardCharsets.UTF_8));
    private MockMvc mvc;
    private PushInstallationService service;
    private AuthSessionRepository sessions;
    private JWTService jwt;
    private User user;
    private String access;
    private Logger logger;
    private Level oldLevel;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach void prepare() {
        // 준비: 실제 서명 검증과 합성 repository를 쓰며 실제 계정/키/서버 설정은 로드하지 않는다.
        service = mock(PushInstallationService.class);
        sessions = mock(AuthSessionRepository.class);
        when(sessions.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(SID), eq(11L), any())).thenReturn(true);
        jwt = new JWTService(KEY, 300000, 3600000, sessions);
        user = User.createLocalUser("synthetic@example.invalid", "unused", "synthetic", Role.USER, "synthetic-public");
        user.setId(11L);
        var principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        access = jwt.generateAccessToken(11L, user.getEmail(), SID);
        when(service.put(any(), anyString(), any())).thenReturn(new Registered(ID, "55555555-5555-4555-8555-555555555555", 1, 1));
        mvc = MockMvcBuilders.standaloneSetup(new PushInstallationController(service, jwt), new ValidationProbe())
                .setControllerAdvice(new PushInstallationAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new ApiLoggingFilter()).build();
        logger = (Logger) LoggerFactory.getLogger(ApiLoggingFilter.class);
        oldLevel = logger.getLevel(); logger.setLevel(Level.INFO);
        logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
    }

    @AfterEach void cleanup() {
        logger.detachAppender(logs); logger.setLevel(oldLevel); logs.stop(); SecurityContextHolder.clearContext();
    }

    @Test void public_response_contains_only_binding_metadata_and_delete_uses_validated_actor() throws Exception {
        // 실행
        var result = mvc.perform(put(BASE + ID).header("Authorization", "Bearer " + access)
                .contentType("application/json").content(json())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.body.installationId").value(ID)).andReturn();
        mvc.perform(delete(BASE + ID).header("Authorization", "Bearer " + access).contentType("application/json")
                .content("{\"installationProof\":\"" + PROOF + "\",\"generation\":2}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        // 검증
        assertThat(result.getResponse().getContentAsString()).doesNotContain(TOKEN, PROOF, SID, "expoPushToken", "installationProof");
        verify(service).put(eq(new Actor(11, SID)), eq(ID), any(Put.class));
        verify(service).delete(eq(new Actor(11, SID)), eq(ID), eq(new Delete(PROOF, 2)));
        assertRedacted();
    }

    @Test void refresh_legacy_missing_mismatched_or_revoked_access_cannot_register() throws Exception {
        // 준비
        String refresh = jwt.generateRefreshToken(11L, user.getEmail(), SID);
        String legacy = jwt.generateAccessToken(11L, user.getEmail());
        String unknown = jwt.generateAccessToken(11L, user.getEmail(), "66666666-6666-4666-8666-666666666666");
        when(sessions.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(SID), eq(12L), any())).thenReturn(true);
        String different = jwt.generateAccessToken(12L, user.getEmail(), SID);

        // 실행 / 검증
        for (String token : java.util.List.of(refresh, legacy, unknown, different, "malformed")) {
            mvc.perform(put(BASE + ID).header("Authorization", "Bearer " + token).contentType("application/json").content(json()))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.body.errorCode").value("PUSH_AUTH_REQUIRED"));
        }
        mvc.perform(put(BASE + ID).contentType("application/json").content(json())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service); assertRedacted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "duplicate", "wrong-type", "extra-owner", "oversize", "trailing"})
    void rejected_json_never_reflects_or_logs_proof_token_or_parser_detail(String fixture) throws Exception {
        // 준비
        String body = switch (fixture) {
            case "malformed" -> json().substring(0, json().length() - 3);
            case "duplicate" -> json().replace("{", "{\"generation\":1,");
            case "wrong-type" -> json().replace("\"generation\":1", "\"generation\":\"1\"");
            case "extra-owner" -> json().replace("{", "{\"userId\":999,");
            case "trailing" -> json() + " {}";
            default -> json() + " ".repeat(17000);
        };

        // 실행 / 검증
        var result = mvc.perform(put(BASE + ID).header("Authorization", "Bearer " + access).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body.errorCode").value("PUSH_INVALID_INPUT")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(TOKEN, PROOF, "JsonParseException", "rejectedValue");
        verifyNoInteractions(service); assertRedacted();
    }

    @Test void unexpected_service_failure_and_validation_exception_are_fixed_and_secret_safe() throws Exception {
        // 준비
        when(service.put(any(), anyString(), any())).thenThrow(new IllegalStateException(TOKEN + PROOF));

        // 실행 / 검증
        var result = mvc.perform(put(BASE + ID).header("Authorization", "Bearer " + access).contentType("application/json").content(json()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.body.errorCode").value("PUSH_REGISTRY_UNAVAILABLE")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(TOKEN, PROOF, "IllegalStateException");
        mvc.perform(post(BASE + "validation-probe").contentType("application/json").content(json()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body.errorCode").value("PUSH_INVALID_INPUT"));
        assertRedacted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/me/%70ush-installations/", "/api/v1/%75sers/me/push-installations/"})
    void decoded_servlet_path_is_redacted_even_when_raw_uri_percent_encodes_prefix(String rawPrefix) throws Exception {
        // 준비: container가 제공하는 rawURI와 decoded servletPath를 별도로 표현한다.
        var request = new MockHttpServletRequest("PUT", rawPrefix + ID);
        request.setServletPath(BASE + ID); request.setContentType("application/json"); request.setContent(json().getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        // 실행: 오류 경로의 응답 원문도 로깅하지 않는다.
        new ApiLoggingFilter().doFilter(request, response, (ignored, output) -> output.getWriter().write(TOKEN + PROOF));

        // 검증
        assertRedacted();
        assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList()).allMatch(value -> !value.contains(ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"push-targets/query", "push-installations/invalidate"})
    void internal_push_raw_and_decoded_paths_never_log_request_response_or_uri_markers(String operation) throws Exception {
        // 준비: 두 내부 endpoint의 raw/decoded 경로를 각각 검사한다. 실제 token과 remote 호출은 사용하지 않는다.
        String requestMarker = "SYNTHETIC_REQUEST_MARKER_NEVER_LOG";
        String responseMarker = "SYNTHETIC_RESPONSE_MARKER_NEVER_LOG";
        String queryMarker = "SYNTHETIC_URI_MARKER_NEVER_LOG";
        String decodedPath = "/internal/v1/chat/" + operation;
        for (String rawPath : java.util.List.of(decodedPath, "/%69nternal/v1/chat/" + operation,
                "/internal/v1/chat/" + operation.replace("push-", "%70ush-"))) {
            logs.list.clear();
            var request = new MockHttpServletRequest("POST", rawPath);
            request.setServletPath(decodedPath);
            request.setQueryString("marker=" + queryMarker);
            request.setContentType("application/json");
            request.setContent((requestMarker + TOKEN + PROOF).getBytes(StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse();

            // 실행: query의 target token과 invalidate 오류 응답 모두 민감하므로 본문을 기록하지 않는다.
            new ApiLoggingFilter().doFilter(request, response, (ignored, output) ->
                    output.getWriter().write("{\"targets\":[{\"expoPushToken\":\"" + TOKEN + "\"}],\"marker\":\"" + responseMarker + "\"}"));

            // 검증: 인코딩된 첫 prefix도 동일한 상수 URI와 라벨로만 기록한다.
            String text = String.join("\n", logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
            assertThat(text).contains("PUSH_INTERNAL_REDACTED", "/internal/v1/chat/push/[REDACTED]")
                    .doesNotContain(requestMarker, responseMarker, queryMarker, TOKEN, PROOF, rawPath, decodedPath);
        }
    }

    private void assertRedacted() {
        String text = String.join("\n", logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        assertThat(text).contains("PUSH_INSTALLATION_REDACTED").doesNotContain(TOKEN, PROOF, access, user.getEmail());
    }

    private static String json() throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("provider", "expo", "expoPushToken", TOKEN, "platform", "android",
                "appEnvironment", "development", "projectId", PROJECT, "installationProof", PROOF, "generation", 1));
    }

    @RestController static class ValidationProbe {
        @PostMapping("/api/v1/users/me/push-installations/validation-probe")
        void invalid() throws Exception {
            var body = new Put("expo", TOKEN, "android", "development", PROJECT, PROOF, 1);
            var errors = new BeanPropertyBindingResult(body, "push");
            errors.addError(new FieldError("push", "expoPushToken", TOKEN, false, null, null, PROOF));
            var method = PushInstallationController.class.getMethod("put", UserPrincipal.class, String.class, jakarta.servlet.http.HttpServletRequest.class);
            throw new MethodArgumentNotValidException(new MethodParameter(method, 0), errors);
        }
    }
}
