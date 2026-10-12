package jp.co.translacat.domain.user.auth;

import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolationException;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {
        "jp.co.translacat.domain.user.auth",
        "jp.co.translacat.domain.user.controller"
})
public class AuthExceptionAdvice {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ResponseDto<AuthError>> handleAuth(AuthException exception) {
        return error(exception.getStatus(), exception.getErrorCode(), exception.getMessage(),
                exception.getRetryAfterSeconds());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseDto<AuthError>> handleCredentials(AuthenticationException exception) {
        // 사용자 존재 여부나 비밀번호 입력값을 응답·로그에 드러내지 않는다.
        return error(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
                "이메일 또는 비밀번호를 확인해주세요.", null);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ResponseDto<AuthError>> handleInput(Exception exception) {
        // 검증 예외의 원문에는 rejectedValue(비밀번호·인증 코드)가 포함될 수 있다.
        return error(HttpStatus.BAD_REQUEST, "AUTH_INVALID_INPUT", "입력값을 확인해주세요.", null);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ResponseDto<AuthError>> handleToken(JwtException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "인증이 만료되었습니다. 다시 로그인해주세요.", null);
    }

    private ResponseEntity<ResponseDto<AuthError>> error(HttpStatus status, String code, String message,
                                                        Integer retryAfterSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        if (retryAfterSeconds != null) {
            headers.set(HttpHeaders.RETRY_AFTER, Integer.toString(Math.max(1, retryAfterSeconds)));
        }
        return new ResponseEntity<>(ResponseUtil.ok(status.value(), message,
                new AuthError(code, retryAfterSeconds)), headers, status);
    }

    public record AuthError(String errorCode, Integer retryAfterSeconds) {
    }
}
