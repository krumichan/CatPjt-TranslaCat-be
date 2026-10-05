package jp.co.translacat.infrastructure.novel.client;

public final class NovelGatewayException extends RuntimeException {
    private final int status;
    private final String code;
    private final boolean retryable;

    public NovelGatewayException(int status, String code, boolean retryable) {
        super(code);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    public int status() { return status; }
    public String code() { return code; }
    public boolean retryable() { return retryable; }
}
