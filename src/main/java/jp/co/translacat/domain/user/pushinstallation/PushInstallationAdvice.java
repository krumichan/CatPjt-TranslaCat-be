package jp.co.translacat.domain.user.pushinstallation;

import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.ConstraintViolationException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = PushInstallationController.class)
public class PushInstallationAdvice {
    @ExceptionHandler(PushInstallationException.class)
    ResponseEntity<?> known(PushInstallationException error) { return error(error.status(), error.code()); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() { return error(HttpStatus.CONFLICT, "PUSH_INSTALLATION_CONFLICT"); }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<?> invalid() { return error(HttpStatus.BAD_REQUEST, "PUSH_INVALID_INPUT"); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unavailable() {
        // DTO·parser·DB 예외의 원문과 toString을 응답이나 로그에 반사하지 않는다.
        return error(HttpStatus.SERVICE_UNAVAILABLE, "PUSH_REGISTRY_UNAVAILABLE");
    }

    private ResponseEntity<?> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ResponseUtil.ok(status.value(), "푸시 등록 요청을 처리하지 못했습니다.", new Error(code)));
    }

    public record Error(String errorCode) {}
}
