package jp.co.translacat.domain.support.exception;

import jakarta.validation.ConstraintViolationException;
import jp.co.translacat.global.dto.ErrorDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "jp.co.translacat.domain.support.controller")
public class SupportExceptionAdvice {

    @ExceptionHandler(SupportException.class)
    public ResponseEntity<ResponseDto<ErrorDto>> support(SupportException exception) {
        return ResponseEntity.status(exception.getStatus()).body(
                ResponseUtil.error(
                        exception.getStatus().value(), exception.getMessage(),
                        exception.getErrorCode(), exception, false
                )
        );
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ResponseDto<ErrorDto>> invalid(Exception exception) {
        // 입력 원문이나 내부 역직렬화 정보 대신 공통 검증 오류를 반환한다.
        return ResponseEntity.badRequest().body(
                ResponseUtil.error(
                        HttpStatus.BAD_REQUEST.value(), "입력값을 확인해 주세요.",
                        "SUPPORT_INVALID_REQUEST", exception, false
                )
        );
    }
}
