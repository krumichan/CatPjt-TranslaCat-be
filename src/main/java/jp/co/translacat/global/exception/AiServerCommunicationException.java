package jp.co.translacat.global.exception;

import lombok.Getter;

@Getter
public class AiServerCommunicationException extends RuntimeException {
    private final String errorCode;

    public AiServerCommunicationException(String message) {
        super(message);
        this.errorCode = "";
    }

    public AiServerCommunicationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiServerCommunicationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "";
    }
}
