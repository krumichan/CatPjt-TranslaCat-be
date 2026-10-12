package jp.co.translacat.domain.user.auth;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthExceptionAdviceTest {
    private final AuthExceptionAdvice advice = new AuthExceptionAdvice();

    @Test
    void neverReflectsRejectedPasswordOrAuthenticationDetails() {
        // 준비
        String secret = "private-password-do-not-reflect";

        // 실행
        var invalidInput = advice.handleInput(new ConstraintViolationException(secret, Set.of()));
        var invalidLogin = advice.handleCredentials(new BadCredentialsException(secret));

        // 검증
        assertThat(invalidInput.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalidInput.getBody().getMessage()).doesNotContain(secret);
        assertThat(invalidLogin.getBody().getMessage()).doesNotContain(secret);
        assertThat(invalidLogin.getBody().getBody().errorCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void exposesStableCodeAndRetryIntervalWithoutCaching() {
        // 준비
        var failure = new AuthException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_EMAIL_RESEND_TOO_SOON", "잠시 후 다시 시도해주세요.", 60);

        // 실행
        var response = advice.handleAuth(failure);

        // 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().getBody().retryAfterSeconds()).isEqualTo(60);
    }
}
