package jp.co.translacat.domain.user.auth;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 인증 실패의 공개 오류 코드와 HTTP 상태만 전달하며 비밀값을 보관하지 않는다. */
@Getter
public class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final Integer retryAfterSeconds;

    public AuthException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public AuthException(HttpStatus status, String errorCode, String message, Integer retryAfterSeconds) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
