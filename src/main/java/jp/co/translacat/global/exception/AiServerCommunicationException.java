package jp.co.translacat.global.exception;

import lombok.Getter;

@Getter
public class AiServerCommunicationException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    public AiServerCommunicationException(String message) {
        super(message);
        this.errorCode = "";
        this.retryable = false;
    }

    public AiServerCommunicationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = false;
    }

    public AiServerCommunicationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "";
        this.retryable = false;
    }

    public AiServerCommunicationException(
            String message,
            AiServerFailureCode failureCode,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = failureCode.name();
        this.retryable = failureCode.isRetryable();
    }
}
