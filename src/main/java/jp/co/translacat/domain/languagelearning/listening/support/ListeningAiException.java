package jp.co.translacat.domain.languagelearning.listening.support;

import jp.co.translacat.global.exception.BusinessException;

import lombok.Getter;

import java.time.Duration;

@Getter
public class ListeningAiException extends BusinessException {

    private final String listeningErrorCode;
    private final String failedStage;
    private final boolean retryable;
    private final Duration retryAfter;
    private final Long resourceId;

    public ListeningAiException(
            String message,
            String errorCode,
            String failedStage,
            boolean retryable,
            Duration retryAfter,
            Long resourceId,
            Throwable cause
    ) {
        super(message, cause);
        this.listeningErrorCode = errorCode;
        this.failedStage = failedStage;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
        this.resourceId = resourceId;
    }

    @Override
    public String getErrorCode() {
        return listeningErrorCode;
    }
}
