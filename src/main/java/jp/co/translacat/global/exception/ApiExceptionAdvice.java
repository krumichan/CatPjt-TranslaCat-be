package jp.co.translacat.global.exception;

import jakarta.persistence.EntityNotFoundException;
import jp.co.translacat.domain.languagelearning.listening.support.ListeningAiException;
import jp.co.translacat.domain.languagelearning.listening.support.ListeningErrorDto;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Objects;
import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class ApiExceptionAdvice {

    @ExceptionHandler(ListeningAiException.class)
    protected ResponseEntity<ResponseDto<ListeningErrorDto>>
    handleListeningAiException(ListeningAiException e) {
        log.error(
                "Listening AI error: code={}, stage={}, resourceId={}",
                e.getErrorCode(),
                e.getFailedStage(),
                e.getResourceId(),
                e
        );
        HttpStatus status = e.isRetryable()
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.UNPROCESSABLE_ENTITY;
        ListeningErrorDto body = new ListeningErrorDto(
                e.getErrorCode(),
                "languageLearning.listening."
                        + e.getErrorCode().toLowerCase(),
                e.isRetryable(),
                e.getRetryAfter() == null
                        ? null
                        : e.getRetryAfter().toSeconds(),
                e.getFailedStage(),
                e.getResourceId()
        );
        ResponseDto<ListeningErrorDto> response =
                ResponseDto.<ListeningErrorDto>builder()
                        .resultCode(status.value())
                        .message(e.getMessage())
                        .body(body)
                        .createDate(LocalDateTime.now())
                        .build();
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseDto<ErrorDto>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e
    ) {
        String responseMessage = this.trace(e);
        log.error("Upload file size exceeded: ", e);
        return this.entity(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", responseMessage, e);
    }

    @ExceptionHandler(AiServerCommunicationException.class)
    protected ResponseEntity<ResponseDto<ErrorDto>> handleAiServerCommunicationException(AiServerCommunicationException e) {
        String responseMessage = this.trace(e);
        log.error("Ai Server Communication logic error: code={}, message={}", e.getErrorCode(), e.getMessage());

        return this.entity(HttpStatus.BAD_REQUEST, e.getErrorCode(), responseMessage, e);
    }

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<?> handleBusinessException(BusinessException e) {
        String responseMessage = this.trace(e);
        log.error("Business logic error: code={}, message={}", e.getErrorCode(), e.getMessage());

        if (isListeningContractError(e.getErrorCode())) {
            HttpStatus status = listeningBusinessStatus(e.getErrorCode());
            ListeningErrorDto body = new ListeningErrorDto(
                    e.getErrorCode(),
                    "languageLearning.listening."
                            + e.getErrorCode().toLowerCase(),
                    false,
                    null,
                    null,
                    null
            );
            ResponseDto<ListeningErrorDto> response =
                    ResponseDto.<ListeningErrorDto>builder()
                            .resultCode(status.value())
                            .message(e.getMessage())
                            .body(body)
                            .createDate(LocalDateTime.now())
                            .build();
            return ResponseEntity.status(status).body(response);
        }

        return this.entity(
                HttpStatus.BAD_REQUEST,
                e.getErrorCode(),
                responseMessage,
                e
        );
    }

    private boolean isListeningContractError(String errorCode) {
        return errorCode != null
                && (errorCode.startsWith("LISTENING_")
                || errorCode.startsWith("AI_"));
    }

    private HttpStatus listeningBusinessStatus(String errorCode) {
        if ("LISTENING_ACTIVE_SESSION_EXISTS".equals(errorCode)
                || "LISTENING_IDEMPOTENCY_CONFLICT".equals(errorCode)) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
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
