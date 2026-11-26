package jp.co.translacat.domain.cameratranslation;

import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

/** 카메라 엔드포인트의 오류를 민감한 원문 없이 반환한다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CameraTranslationController.class)
class CameraTranslationErrors {
    /** 업무 오류에 실제 HTTP 상태와 요청 제한 헤더를 지정한다. */
    @ExceptionHandler(CameraTranslationProblem.class)
    ResponseEntity<ResponseDto<Map<String, String>>> problem(CameraTranslationProblem error) {
        var response = ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore());
        if (error.retryAfter() != null) {
            response.header(HttpHeaders.RETRY_AFTER, error.retryAfter().toString());
        }
        return response.body(ResponseUtil.ok(error.status(), error.body()));
    }

    /** UUID·JSON·필수 입력 바인딩 오류를 안정된 메시지로 변환한다. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            ServletRequestBindingException.class, MissingServletRequestPartException.class})
    ResponseEntity<ResponseDto<Map<String, String>>> invalid() {
        return problem(CameraTranslationProblem.invalid());
    }

    /** 서버 단계에서 거부한 multipart 용량을 입력 오류로 표시한다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ResponseDto<Map<String, String>>> tooLarge() {
        return problem(new CameraTranslationProblem(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE"));
    }

    /** 지원 포맷으로 접수할 수 없는 요청을 거부한다. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ResponseDto<Map<String, String>>> unsupported() {
        return problem(new CameraTranslationProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE"));
    }

    /** DB 오류가 외부 응답에 SQL과 파라미터를 포함하지 않게 한다. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ResponseDto<Map<String, String>>> unavailable() {
        return problem(CameraTranslationProblem.unavailable());
    }
}
