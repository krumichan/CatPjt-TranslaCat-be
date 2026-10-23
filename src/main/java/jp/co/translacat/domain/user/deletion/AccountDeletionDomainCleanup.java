package jp.co.translacat.domain.user.deletion;

/** 각 도메인은 자체 저장 객체와 진행 작업 fence까지 끝나야 true를 반환한다. */
public interface AccountDeletionDomainCleanup {
    String domain();
    void requireReady();
    boolean clean(AccountDeletionStore.Operation operation);
}
