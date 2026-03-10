package jp.co.translacat.global.exception;

public class ExternalApiInvocationException extends RuntimeException {
    public ExternalApiInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
