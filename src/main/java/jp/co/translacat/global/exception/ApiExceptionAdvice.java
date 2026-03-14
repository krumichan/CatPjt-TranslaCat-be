package jp.co.translacat.global.exception;

import jakarta.persistence.EntityNotFoundException;
import jp.co.translacat.global.dto.ErrorDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ExceptionUtil;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class ApiExceptionAdvice {

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ResponseDto<ErrorDto>> handleBusinessException(BusinessException e) {
        String responseMessage = this.trace(e);
        log.error("Business logic error: code={}, message={}", e.getErrorCode(), e.getMessage());

        return this.entity(HttpStatus.BAD_REQUEST, e.getErrorCode(), responseMessage, e);
    }

    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ResponseDto<ErrorDto>> handleAccessDeniedException(AccessDeniedException e) {
        String responseMessage = this.trace(e);
        log.error("Access denied: ", e);
        return this.entity(HttpStatus.FORBIDDEN, "ACCESS_DENIED", responseMessage, e);
    }

    @ExceptionHandler(GeminiPartialProcessingException.class)
    protected ResponseEntity<ResponseDto<ErrorDto>> handleGeminiPartialProcessingException(GeminiPartialProcessingException e) {
        String responseMessage = this.trace(e);
        log.error("Failed to translate or save data in DB: ", e);
        return this.entity(HttpStatus.BAD_GATEWAY, "GEMINI_PARTIAL_PROCESSING_ERROR", responseMessage, e);
    }

    @ExceptionHandler(ExternalApiInvocationException.class)
    protected ResponseEntity<ResponseDto<ErrorDto>> handleExternalApiInvocationException(ExternalApiInvocationException e) {
        String responseMessage = this.trace(e);
        log.error("External API call failed: ", e);
        return this.entity(HttpStatus.BAD_GATEWAY, "EXTERNAL_API_ERROR", responseMessage, e);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    protected ResponseEntity<ResponseDto<ErrorDto>> handleEntityNotFoundException(EntityNotFoundException e) {
        String responseMessage = this.trace(e);
        log.error("Entity not found: ", e);
        return this.entity(HttpStatus.NOT_FOUND, "", responseMessage, e);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ResponseDto<ErrorDto>> exception(Exception e) {
        String responseMessage = this.trace(e);
        log.error("Exception occurred: ", e);
        return this.entity(HttpStatus.INTERNAL_SERVER_ERROR, "", responseMessage, e);
    }

    private String trace(Exception e) {
        String errorMessage = e.getMessage();
        if (Objects.isNull(errorMessage)) {
            try {
                errorMessage = ExceptionUtil.convertStackTrace(e);
            } catch (Exception ex) {
                errorMessage = "Failed to read stack trace of exception.";
            }
        }
        return "Message <" + errorMessage +">";
    }

    private ResponseEntity<ResponseDto<ErrorDto>> entity(
            HttpStatus status, String errorCode, String responseMessage, Exception e) {
        ResponseDto<ErrorDto> errorVo =  ResponseUtil.error(
                status.value(), responseMessage, errorCode, e, false);
        return new ResponseEntity<>(errorVo, null, status);
    }
}
