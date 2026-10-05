package jp.co.translacat.global.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@UtilityClass
public class TransactionUtil {

    public static void runAfterCompletion(Runnable runnable) {
        registerSync(null, runnable);
    }

    public static void runAfterCommit(Runnable runnable) {
        // 본문 저장은 상위 작업이 실패/롤백한 경우 실행하지 않는다.
        registerSync(TransactionSynchronization.STATUS_COMMITTED, runnable);
    }

    private static void registerSync(Integer targetStatus, Runnable runnable) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (targetStatus == null || status == targetStatus) {
                        runnable.run();
                    }
                }
            });
        } else {
            log.debug("No active transaction found. Executing immediately.");
            runnable.run();
        }
    }
}
