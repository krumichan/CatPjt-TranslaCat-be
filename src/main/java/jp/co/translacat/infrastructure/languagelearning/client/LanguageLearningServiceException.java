package jp.co.translacat.infrastructure.languagelearning.client;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LanguageLearningServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public LanguageLearningServiceException(
            HttpStatus status,
            String errorCode,
            String message
    ) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public LanguageLearningServiceException(
            HttpStatus status,
            String errorCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
