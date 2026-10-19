package jp.co.translacat.domain.user.auth.mobile;

import io.jsonwebtoken.JwtException;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.global.security.JWTService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobileAuthPolicyTest {
    @Test
    void s256_matches_rfc7636_appendix_b_without_padding() {
        // 준비: RFC 7636 Appendix B의 공개 검증 벡터이며 실사용 자격이 아니다.
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

        // 실행·검증
        assertThat(MobileAuthProofs.challenge(verifier)).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        assertThat(MobileAuthProofs.issueCode()).matches("[A-Za-z0-9_-]{43}");
        assertThat(MobileAuthProofs.same("same", "same")).isTrue();
        assertThat(MobileAuthProofs.same("same", "different")).isFalse();
    }

    @Test
    void production_requires_explicit_allowlist_and_development_defaults_are_not_inherited() {
        // 준비
        var production = new MobileAuthRedirects("", true);
        var configured = new MobileAuthRedirects("translacat://auth/callback", true);
        var defaults = new MockEnvironment();
        defaults.setDefaultProfiles("prod");
        var defaultProduction = new MobileAuthRedirects("", defaults);

        // 실행·검증: 설정 없는 운영 모바일 인증은 fail-closed이고 명시 경로만 허용한다.
        assertThatThrownBy(() -> production.requireAllowed("translacat-dev://auth/callback"))
                .isInstanceOfSatisfying(AuthException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo("MOBILE_AUTH_NOT_CONFIGURED"));
        assertThatThrownBy(() -> defaultProduction.requireAllowed("translacat-dev://auth/callback"))
                .isInstanceOfSatisfying(AuthException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo("MOBILE_AUTH_NOT_CONFIGURED"));
        configured.requireAllowed("translacat://auth/callback");
        assertThatThrownBy(() -> configured.requireAllowed("translacat-dev://auth/callback")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> new MobileAuthRedirects("https://evil.invalid/callback", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid mobile redirect allowlist configuration");
    }

    @Test
    void absent_session_store_only_supports_legacy_tokens_and_never_allows_sid_tokens() {
        // 준비: DB 없는 보안 slice는 구형 토큰 검증만 가능해야 한다.
        var jwt = new JWTService("bW9iaWxlLXNlY3VyaXR5LXNsaWNlLXN5bnRoZXRpYy1rZXktMjAyNg==", 60000, 600000);
        String legacy = jwt.generateAccessToken(11L, "slice@example.invalid");
        String modern = jwt.generateAccessToken(11L, "slice@example.invalid", UUID.randomUUID().toString());

        // 실행·검증
        assertThat(jwt.getAccessId(legacy)).isEqualTo(11L);
        assertThatThrownBy(() -> jwt.getAccessId(modern)).isInstanceOf(JwtException.class);
    }
}
