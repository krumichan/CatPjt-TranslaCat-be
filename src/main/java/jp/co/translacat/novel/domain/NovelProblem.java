package jp.co.translacat.novel.domain;

public class NovelProblem extends RuntimeException {
    private final String code;
    private final int status;
    private final boolean retryable;
    private final long retryAfterMillis;

    public NovelProblem(String code, int status) {
        this(code, status, false, 0);
    }

    public NovelProblem(String code, int status, boolean retryable, long retryAfterMillis) {
        super(code);
        this.code = code;
        this.status = status;
        this.retryable = retryable;
        this.retryAfterMillis = retryAfterMillis;
    }

    public String code() { return code; }
    public int status() { return status; }
    public boolean retryable() { return retryable; }
    public long retryAfterMillis() { return retryAfterMillis; }
}
