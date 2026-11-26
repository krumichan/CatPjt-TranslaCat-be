package jp.co.translacat.domain.wordbook;

import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import jp.co.translacat.domain.user.deletion.AccountDeletionDomainCleanup;
import jp.co.translacat.domain.user.deletion.AccountDeletionStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/** 단어장 소유 데이터의 계정 삭제와 만료된 요청 기록의 정리를 담당한다. */
@Component
class WordbookCleanup implements AccountDeletionDomainCleanup {
    private final WordbookStore store;
    private final AccountDataLifecycleAccess accounts;
    private final TransactionTemplate transactions;

    /** 삭제 작업과 사용자 쓰기가 같은 계정 잠금 계약을 사용한다. */
    WordbookCleanup(WordbookStore store, AccountDataLifecycleAccess accounts, PlatformTransactionManager manager) {
        this.store = store;
        this.accounts = accounts;
        this.transactions = new TransactionTemplate(manager);
    }

    /** {@inheritDoc} */
    @Override
    public String domain() {
        return "WORDBOOK";
    }

    /** {@inheritDoc} */
    @Override
    public void requireReady() {
        store.hasCleanupSchema();
    }

    /** {@inheritDoc} */
    @Override
    public boolean clean(AccountDeletionStore.Operation operation) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            // 탈퇴가 접수된 계정은 진행 중인 쓰기와 같은 순서로 계정을 먼저 잠근다.
            if (!accounts.lockDeletionAccepted(operation.userId())) {
                return false;
            }

            if (store.hasCleanupSchema()) {
                store.deleteOwnedData(operation.userId());
            }
            return true;
        }));
    }

    /** 논리 만료는 조회 즉시 적용하며 저장된 요청 기록은 정기적으로 지운다. */
    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    void purgeExpiredReplays() {
        if (store.hasCleanupSchema()) {
            store.deleteExpiredReplays(Instant.now());
        }
    }
}
