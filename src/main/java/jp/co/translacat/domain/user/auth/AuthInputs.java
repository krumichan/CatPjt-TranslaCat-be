package jp.co.translacat.domain.user.auth;

import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AuthInputs {
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");

    private AuthInputs() {
    }

    public static String normalizeEmail(String value) {
        // 공백/대소문자만 정규화한다. Gmail의 점과 +별칭을 지워 다른 주소를 합치지 않는다.
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int at = email.indexOf('@');
        if (email.length() > 254 || at < 1 || at > 64 || !EMAIL.matcher(email).matches()
                || email.startsWith(".") || email.charAt(at - 1) == '.' || email.substring(0, at).contains("..")) {
            throw invalid("AUTH_EMAIL_INVALID", "올바른 이메일 주소를 입력해주세요.");
        }
        return email;
    }

    public static void validatePassword(String password) {
        // BCrypt의 입력 한도는 문자 수가 아닌 UTF-8 바이트 수로 검사한다. 비밀번호는 trim하지 않는다.
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalid("AUTH_PASSWORD_INVALID", "비밀번호는 8자 이상, UTF-8 기준 72바이트 이하로 입력해주세요.");
        }
    }

    public static String validateNickname(String value) {
        String nickname = value == null ? "" : value.trim();
        if (nickname.isEmpty() || nickname.length() > 30) {
            throw invalid("AUTH_NICKNAME_INVALID", "닉네임은 1자 이상 30자 이하로 입력해주세요.");
        }
        return nickname;
    }

    public static LocalDate validateBirthDate(LocalDate value) {
        // 연령 제한은 별도의 제품 정책이다. 여기서는 날짜 누락과 미래 날짜만 거부한다.
        if (value == null || value.isAfter(LocalDate.now(ZoneOffset.UTC)) || value.getYear() < 1) {
            throw invalid("AUTH_BIRTH_DATE_INVALID", "올바른 생년월일을 입력해주세요.");
        }
        return value;
    }

    private static AuthException invalid(String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message);
    }
}
