package jp.co.translacat.domain.user.auth;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class AuthInputsTest {
    @Test
    void normalizesCaseButKeepsGmailAliasesSeparate() {
        // 준비
        String address = "  First.Last+Cat@Gmail.com  ";

        // 실행
        String normalized = AuthInputs.normalizeEmail(address);

        // 검증
        assertThat(normalized).isEqualTo("first.last+cat@gmail.com");
        assertThatThrownBy(() -> AuthInputs.normalizeEmail("a..b@example.com")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> AuthInputs.normalizeEmail("a@example.com\r\nBcc: x@example.com")).isInstanceOf(AuthException.class);
    }

    @Test
    void enforcesBcryptByteLimitWithoutTrimmingPasswords() {
        // 준비
        String atLimit = "한".repeat(24);
        String overLimit = "한".repeat(25);

        // 실행 / 검증
        assertThatCode(() -> AuthInputs.validatePassword(atLimit)).doesNotThrowAnyException();
        assertThatCode(() -> AuthInputs.validatePassword(" short  ")).doesNotThrowAnyException();
        assertThatThrownBy(() -> AuthInputs.validatePassword(overLimit)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> AuthInputs.validatePassword("        ")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> AuthInputs.validatePassword("1234567")).isInstanceOf(AuthException.class);
    }

    @Test
    void requiresNicknameAndRealNonFutureBirthDate() {
        // 준비
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // 실행 / 검증
        assertThat(AuthInputs.validateNickname("  고양이  ")).isEqualTo("고양이");
        assertThat(AuthInputs.validateBirthDate(today)).isEqualTo(today);
        assertThatThrownBy(() -> AuthInputs.validateNickname(" ")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> AuthInputs.validateNickname("x".repeat(31))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> AuthInputs.validateBirthDate(null)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> AuthInputs.validateBirthDate(today.plusDays(1))).isInstanceOf(AuthException.class);
    }
}
