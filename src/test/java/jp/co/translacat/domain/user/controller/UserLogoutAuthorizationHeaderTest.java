package jp.co.translacat.domain.user.controller;

import io.jsonwebtoken.JwtException;
import jp.co.translacat.domain.user.repository.RefreshTokenRepository;
import jp.co.translacat.domain.user.repository.UserAllowedRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.domain.user.service.OAuth2AuthenticationService;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.JWTService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserLogoutAuthorizationHeaderTest {
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final JWTService jwt = new JWTService(Base64.getEncoder().encodeToString(new byte[32]), 60000, 60000);
    private final UserService users = new UserService(mock(UserRepository.class), refreshTokens,
            mock(UserAllowedRepository.class), jwt, mock(AuthenticationManager.class), mock(PasswordEncoder.class));
    private final UserController controller = new UserController(users, mock(OAuth2AuthenticationService.class));

    @Test
    void bearerLogoutDeletesOnlyTheAuthenticatedUsersRefreshToken() {
        // 준비: 실제 JWT 서명 검증은 유지하고 DB 저장소만 모의한다.
        String token = jwt.generateAccessToken(9L, "logout@example.invalid");

        // 실행
        var response = controller.logout("Bearer " + token);

        // 검증: 헤더 접두사가 파서로 들어가지 않으며 다른 사용자의 토큰에는 접근하지 않는다.
        assertEquals(200, response.getResultCode());
        assertEquals("SUCCESS", response.getBody());
        verify(refreshTokens).deleteById(9L);
        verifyNoMoreInteractions(refreshTokens);
    }

    @Test
    void missingOrUnsupportedAuthorizationNeverDeletesRefreshTokens() {
        // 준비
        String token = jwt.generateAccessToken(9L, "logout@example.invalid");

        // 실행·검증: 인증 헤더 없는 직접 호출이나 compact JWT 자체를 HTTP 인증으로 받아들이지 않는다.
        for (String header : new String[]{null, "", "Bearer ", "Bearer   ", "Basic invalid", token}) {
            var failure = assertThrows(BusinessException.class, () -> controller.logout(header));
            assertEquals("INVALID_AUTHORIZATION_HEADER", failure.getErrorCode());
        }
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void malformedOrUntrustedJwtNeverDeletesRefreshTokens() {
        // 준비: 올바른 형식이라도 다른 키로 서명한 토큰은 기존 JWT 검증에서 거부해야 한다.
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        var untrusted = new JWTService(Base64.getEncoder().encodeToString(otherKey), 60000, 60000);
        String token = untrusted.generateAccessToken(9L, "logout@example.invalid");

        // 실행·검증
        assertThrows(JwtException.class, () -> controller.logout("Bearer malformed.jwt"));
        assertThrows(JwtException.class, () -> controller.logout("Bearer " + token));
        verifyNoInteractions(refreshTokens);
    }
}
