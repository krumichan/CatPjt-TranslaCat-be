package jp.co.translacat.global.exception;

/**
 * Safe, bounded classifications for AI server transport and protocol failures.
 */
public enum AiServerFailureCode {
    CIRCUIT_OPEN(true),
    CONNECT_FAILURE(true),
    CONNECT_TIMEOUT(true),
    HTTP_4XX(false),
    HTTP_5XX(true),
    RESPONSE_DECODE(false),
    CONFIGURATION(false),
    UNKNOWN(false);

    private final boolean retryable;

    AiServerFailureCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
