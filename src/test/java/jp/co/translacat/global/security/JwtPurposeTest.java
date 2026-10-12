package jp.co.translacat.global.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtPurposeTest {
    private final byte[] key = new byte[32];
    private final JWTService jwt = new JWTService(Base64.getEncoder().encodeToString(key), 60_000, 600_000);

    private UserPrincipal principal(long id) {
        User user = User.createLocalUser("cat@example.com", "encoded", "Cat", Role.USER, "fixture-public-id");
        user.setId(id);
        return new UserPrincipal(user);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshCannotAuthenticateApiAndAccessCannotRotateSession() {
        // 준비
        String access = jwt.generateAccessToken(1L, "cat@example.com");
        String refresh = jwt.generateRefreshToken(1L, "cat@example.com");

        // 실행 / 검증
        assertThat(jwt.validateToken(access, principal(1L))).isTrue();
        assertThat(jwt.validateToken(access, principal(2L))).isFalse();
        assertThat(jwt.validateToken(refresh, principal(1L))).isFalse();
        assertThatThrownBy(() -> jwt.getAccessId(refresh)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.getRefreshId(access)).isInstanceOf(JwtException.class);
        assertThat(jwt.getRefreshId(refresh)).isEqualTo(1L);
        assertThat(jwt.generateRefreshToken(1L, "cat@example.com")).isNotEqualTo(refresh);
    }

    @Test
    void acceptsShortLegacyAccessButRejectsLegacyRefreshForApi() {
        // 준비
        String shortToken = legacy(60_000);
        String longToken = legacy(600_000);
        var ambiguous = new JWTService(Base64.getEncoder().encodeToString(key), 600_000, 600_000);

        // 실행 / 검증
        assertThat(jwt.validateToken(shortToken, principal(1L))).isTrue();
        assertThat(jwt.validateToken(longToken, principal(1L))).isFalse();
        assertThat(jwt.getRefreshId(longToken)).isEqualTo(1L);
        assertThat(ambiguous.validateToken(shortToken, principal(1L))).isFalse();
    }

    @Test
    void filterLeavesRefreshAndMalformedRequestsUnauthenticated() throws Exception {
        // 준비
        var context = mock(ApplicationContext.class);
        var filter = new JwtFilter(jwt, context);

        // 실행: 인증 실패는 다음 보안 필터가 처리하며 사용자 조회까지 진행하지 않는다.
        for (String token : new String[]{jwt.generateRefreshToken(1L, "cat@example.com"), "malformed"}) {
            var request = new MockHttpServletRequest("GET", "/api/v1/auth/account");
            request.addHeader("Authorization", "Bearer " + token);
            var continued = new AtomicBoolean();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> continued.set(true));

            // 검증
            assertThat(continued).isTrue();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
        verifyNoInteractions(context);
    }

    private String legacy(long lifetime) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject("cat@example.com").claim("id", 1L)
                .issuedAt(new Date(now)).expiration(new Date(now + lifetime))
                .signWith(Keys.hmacShaKeyFor(key)).compact();
    }
}
