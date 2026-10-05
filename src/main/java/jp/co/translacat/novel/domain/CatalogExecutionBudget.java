package jp.co.translacat.novel.domain;

/** 목록 대기와 공급자 실행을 분리하고, 같은 결과의 저장에만 별도 여유를 둔다. */
public final class CatalogExecutionBudget {
    public static final long QUEUE_MS = 300000;
    public static final long PROVIDER_MS = 120000;
    public static final long COMMIT_GRACE_MS = 30000;
    public static final long LEASE_MS = PROVIDER_MS + COMMIT_GRACE_MS;

    private CatalogExecutionBudget() {}
}
