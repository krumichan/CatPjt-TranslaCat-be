package jp.co.translacat.domain.user.deletion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Component
public class AccountDeletionWorker {
    private final AccountDeletionProperties properties;
    private final AccountDeletionStore store;
    private final AccountDeletionRemoteClients remote;
    private final AccountDeletionPersonalData personal;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AccountDeletionWorker(AccountDeletionProperties properties, AccountDeletionStore store,
                                 AccountDeletionRemoteClients remote, AccountDeletionPersonalData personal,
                                 JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.properties = properties;
        this.store = store;
        this.remote = remote;
        this.personal = personal;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${account.deletion.worker-delay-ms:30000}", initialDelayString = "${account.deletion.worker-initial-delay-ms:60000}")
    public void run() {
        if (!properties.isWorkerEnabled()) return;
        for (String operationId : store.pending(Instant.now())) process(operationId);
        store.expireReceipts(Instant.now());
    }

    public void process(String id) {
        var operation = store.operation(id, false).orElse(null);
        if (operation == null || !"PENDING".equals(operation.status())) return;
        for (String step : store.remaining(id)) {
            String lease = UUID.randomUUID().toString();
            if (!Boolean.TRUE.equals(tx.execute(status -> store.claim(id, step, lease, Instant.now())))) continue;
            boolean completed = false;
            try {
                // 외부 서비스/실제 객체 삭제는 DB 트랜잭션 밖이다. 실패한 같은 operation은 다시 시도한다.
                completed = switch (step) {
                    case "CHAT", "LL" -> remote.delete(step, operation);
                    case "BE_DATA" -> personal.clean(operation);
                    case "BE_OBJECTS" -> personal.cleanObjects(operation.operationId());
                    default -> false;
                };
            } catch (RuntimeException ignored) {
                // 비밀이나 본문을 포함할 수 있는 예외를 작업 테이블/로그에 저장하지 않는다.
            }
            boolean result = completed;
            tx.executeWithoutResult(status -> store.finish(id, step, lease, result, Instant.now()));
        }
        tx.executeWithoutResult(status -> {
            store.operation(id, true).ifPresent(current -> {
                if (store.remaining(id).isEmpty()) {
                    jdbc.update("update `user` set account_state='DELETED' where id=? and account_state='DELETION_PENDING'", current.userId());
                    store.complete(id, Instant.now());
                }
            });
        });
    }
}
