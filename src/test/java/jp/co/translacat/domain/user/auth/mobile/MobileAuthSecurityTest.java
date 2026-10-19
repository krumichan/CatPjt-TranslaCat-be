package jp.co.translacat.domain.user.auth.mobile;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jp.co.translacat.domain.user.auth.AuthExceptionAdvice;
import jp.co.translacat.domain.user.dto.UserLoginResponseDto;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.config.SecurityConfig;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.JwtFilter;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MobileAuthController.class, properties = {
        "cors.allowed-origin=http://localhost:3000",
        "jwt.token.secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.token.expired.access=300000",
        "jwt.token.expired.refresh=604800000",
        "MOBILE_AUTH_APPROVAL_SECRET=synthetic-mobile-approval-key-for-tests-2026"
})
@Import({SecurityConfig.class, JwtFilter.class, JWTService.class, ApiLoggingFilter.class, AuthExceptionAdvice.class,
        MobileAuthApprovalVerifier.class})
@ContextConfiguration(classes = MobileAuthController.class)
class MobileAuthSecurityTest {
    private static final String ID = "11111111-1111-4111-8111-111111111111";
    private static final String STATE = "s".repeat(43);
    private static final String CODE = "c".repeat(43);
    @Autowired MockMvc mvc;
    @Autowired JWTService jwt;
    @MockitoBean MobileAuthService mobile;

    @Test
    void anonymous_authorize_is_rejected_before_service_or_body_processing() throws Exception {
        // 실행·검증: 익명 생성·교환 matcher가 승인 경로를 열지 않는다.
        mvc.perform(post("/api/v1/auth/mobile/authorize").contentType("application/json")
                        .content("{\"requestId\":\"" + ID + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/mobile/requests")).andExpect(status().isUnauthorized());
        verifyNoInteractions(mobile);
    }

    @Test
    void anonymous_request_and_exchange_keep_existing_envelope_and_disable_caching() throws Exception {
        // 준비: 교환 API의 민감한 응답도 기존 body DTO 형식을 유지한다.
        when(mobile.create(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new MobileAuthService.RequestResponse(ID, 300));
        when(mobile.exchange(eq(ID), eq(CODE), anyString(), anyString())).thenReturn(UserLoginResponseDto.builder()
                .accessToken("synthetic-access").refreshToken("synthetic-refresh").accessTokenExpiresIn(300L)
                .role(Role.USER).publicId("synthetic-public-id").build());

        // 실행·검증
        mvc.perform(post("/api/v1/auth/mobile/requests").contentType("application/json").content("""
                        {"codeChallenge":"%s","state":"%s","redirectUri":"translacat-dev://auth/callback"}
                        """.formatted("h".repeat(43), STATE)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.body.requestId").value(ID)).andExpect(jsonPath("$.body.expiresIn").value(300));
        mvc.perform(post("/api/v1/auth/mobile/exchange").contentType("application/json").content("""
                        {"requestId":"%s","code":"%s","codeVerifier":"%s"}
                        """.formatted(ID, CODE, "v".repeat(43))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.body.accessToken").value("synthetic-access"))
                .andExpect(jsonPath("$.body.refreshToken").value("synthetic-refresh"))
                .andExpect(jsonPath("$.body.sessionId").doesNotExist());
    }

    @Test
    void authorize_uses_authenticated_user_and_never_logs_callback_code_or_request_proofs() throws Exception {
        // 준비: 실제 HTTP 로그 필터를 관찰하고 승인 주체를 보안 principal로만 전달한다.
        User account = User.createLocalUser("synthetic@example.invalid", "synthetic-hash", "Synthetic", Role.USER, "fixture");
        account.setId(41L);
        String access = jwt.generateAccessToken(41L, account.getEmail());
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = MobileAuthApprovalVerifierTest.sign(ID, access, timestamp);
        when(mobile.authorize(ID, 41L)).thenReturn(new MobileAuthService.AuthorizeResponse(
                "translacat-dev://auth/callback", CODE, STATE));
        Logger logger = (Logger) LoggerFactory.getLogger(ApiLoggingFilter.class);
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try {
            // 실행
            mvc.perform(post("/api/v1/auth/mobile/authorize").with(user(new UserPrincipal(account)))
                            .header("Authorization", "Bearer " + access)
                            .header(MobileAuthApprovalVerifier.TIMESTAMP_HEADER, timestamp)
                            .header(MobileAuthApprovalVerifier.SIGNATURE_HEADER, signature)
                            .contentType("application/json").content("{\"requestId\":\"" + ID + "\"}"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.body.redirectUri").value("translacat-dev://auth/callback"))
                    .andExpect(jsonPath("$.body.code").value(CODE)).andExpect(jsonPath("$.body.state").value(STATE));

            // 검증: 승인 응답 원문·callback·state·사용자 이메일을 로그에 반사하지 않는다.
            verify(mobile).authorize(ID, 41L);
            assertThat(captured.list).isNotEmpty();
            assertThat(captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
                    .allSatisfy(message -> assertThat(message).doesNotContain(CODE, STATE, access, signature,
                            MobileAuthApprovalVerifierTest.KEY, "translacat-dev://", "synthetic@example.invalid"));
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void valid_access_without_bff_approval_or_with_a_signature_for_other_access_is_rejected_before_authorize() throws Exception {
        // 준비: access와 사용자 principal 자체는 유효해도 웹 승인 증명을 대체하지 않는다.
        User account = User.createLocalUser("synthetic@example.invalid", "synthetic-hash", "Synthetic", Role.USER, "fixture");
        account.setId(41L);
        String access = jwt.generateAccessToken(41L, account.getEmail());
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String otherSignature = MobileAuthApprovalVerifierTest.sign(ID, "other.access.token", timestamp);

        // 실행·검증: 두 요청 모두 DB 승인/코드 발급 서비스까지 도달하지 않는다.
        mvc.perform(post("/api/v1/auth/mobile/authorize").with(user(new UserPrincipal(account)))
                        .header("Authorization", "Bearer " + access).contentType("application/json")
                        .content("{\"requestId\":\"" + ID + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.body.errorCode").value("MOBILE_AUTH_APPROVAL_INVALID"));
        mvc.perform(post("/api/v1/auth/mobile/authorize").with(user(new UserPrincipal(account)))
                        .header("Authorization", "Bearer " + access)
                        .header(MobileAuthApprovalVerifier.TIMESTAMP_HEADER, timestamp)
                        .header(MobileAuthApprovalVerifier.SIGNATURE_HEADER, otherSignature)
                        .contentType("application/json").content("{\"requestId\":\"" + ID + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.body.errorCode").value("MOBILE_AUTH_APPROVAL_INVALID"));
        verifyNoInteractions(mobile);
    }
}
