package jp.co.translacat.domain.documenttranslation;

import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** 객체 저장을 계정 삭제 경계에 등록하고 늦은 PUT의 정리 근거를 유지한다. */
@Component
final class DocumentObjectWrites {
    private static final String UUID_PATTERN = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    private static final Pattern KEY = Pattern.compile("(?:source-" + UUID_PATTERN
            + "|result-" + UUID_PATTERN + "-" + UUID_PATTERN
            + "|batch-" + UUID_PATTERN + "-" + UUID_PATTERN + "-[0-9]{1,4})");

    private final JdbcTemplate jdbc;
    private final AccountDataLifecycleAccess accounts;
    private final TransactionTemplate transactions;
    private final TransactionTemplate independent;

    // 객체 쓰기 의도는 호출자 트랜잭션이 rollback되어도 사라지지 않게 별도 경계를 둔다.
    DocumentObjectWrites(JdbcTemplate jdbc, PlatformTransactionManager manager, AccountDataLifecycleAccess accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        transactions = new TransactionTemplate(manager);
        independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 표시·접수·결과 변경은 기존 탈퇴 접수와 같은 계정 잠금 아래 판정한다.
    <T> T whenActive(long owner, T denied, Supplier<T> operation) {
        return transactions.execute(status -> accounts.lockActive(owner) ? operation.get() : denied);
    }

    // 계정과 현재 작업 권한이 유지된 시도만 저장을 시작하고, 외부 저장은 DB 잠금 밖에서 수행한다.
    boolean store(DocumentFiles files, long owner, UUID job, String key, Path source, BooleanSupplier permitted)
            throws IOException {
        requireOwnedKey(job, key);
        Boolean reserved = independent.execute(status -> {
            if (!accounts.lockActive(owner) || !permitted.getAsBoolean()) {
                return false;
            }
            jdbc.update("""
                    INSERT INTO document_translation_object_write (object_key, owner_id, job_id, state, started_at)
                    VALUES (?, ?, ?, 'WRITING', UTC_TIMESTAMP(6))
                    """, key, owner, job.toString());
            return true;
        });
        if (!Boolean.TRUE.equals(reserved)) {
            return false;
        }

        // 실패가 반환돼도 통신 상태가 확정되지 않은 PUT은 정리할 때까지 별도로 추적한다.
        try {
            files.put(key, source, () -> pending(owner, key));
        } catch (DocumentFiles.UnconfirmedWrite unconfirmed) {
            throw unconfirmed;
        } catch (IOException settledFailure) {
            complete(owner, key);
            throw settledFailure;
        }
        complete(owner, key);
        return true;
    }

    // 계정과 문서 작업을 잠근 호출자가 실제 파일 참조를 확정한 같은 트랜잭션에서 호출한다.
    void link(long owner, UUID job, String key) {
        requireOwnedKey(job, key);
        // 재시도가 공유하는 원본은 최초 작업을 만료로 제거한 뒤에도 소유 근거를 남긴다.
        int linked = key.startsWith("source-") ? jdbc.update("""
                UPDATE document_translation_object_write SET state='LINKED'
                WHERE object_key=? AND owner_id=? AND job_id=? AND state='SETTLED'
                """, key, owner, job.toString()) : forget(owner, job, key);
        if (linked != 1) {
            throw new IllegalStateException("Document object was not acknowledged before linking");
        }
    }

    // 최초 작업의 메타데이터가 사라져도 공용 원본의 계정과 키를 대조한다.
    boolean ownsLinkedSource(long owner, UUID rootJob, String key) {
        requireOwnedKey(rootJob, key);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM document_translation_object_write
                WHERE owner_id=? AND object_key=? AND job_id=? AND state='LINKED'
                """, Integer.class, owner, key, rootJob.toString());
        return count != null && count == 1;
    }

    // 원본 삭제가 성공했고 마지막 재시도 참조까지 지워진 경우에만 소유 근거를 제거한다.
    void clearExpiredSource(long owner, String key) {
        if (key != null) {
            jdbc.update("""
                    DELETE FROM document_translation_object_write
                    WHERE owner_id=? AND object_key=? AND state='LINKED'
                    AND NOT EXISTS (
                        SELECT 1 FROM document_translation_job WHERE owner_id=? AND input_key=?
                    )
                    """, owner, key, owner, key);
        }
    }

    // 확정된 쓰기만 정리한다. 없으면 다른 정리 호출이 앞서 처리했을 수 있다.
    private int forget(long owner, UUID job, String key) {
        return jdbc.update("""
                DELETE FROM document_translation_object_write
                WHERE object_key=? AND owner_id=? AND job_id=? AND state='SETTLED'
                """, key, owner, job.toString());
    }

    // 미접수·취소 경합에서 완료가 확정된 고아 객체만 바로 지울 수 있다.
    void removeOrphan(DocumentFiles files, long owner, UUID job, String key) throws IOException {
        requireOwnedKey(job, key);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM document_translation_object_write
                WHERE object_key=? AND owner_id=? AND job_id=? AND state='SETTLED'
                """, Integer.class, key, owner, job.toString());
        if (count != null && count == 1) {
            files.delete(key);
            forget(owner, job, key);
        }
    }

    // 작업에 연결되지 않은 오래된 파일을 제한된 수로 다시 확인한다.
    void cleanOldOrphans(DocumentFiles files) throws IOException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        var affected = jdbc.query("""
                SELECT owner_id,job_id,object_key,state FROM document_translation_object_write
                WHERE state IN ('WRITING','SETTLED') AND started_at < UTC_TIMESTAMP(6) - INTERVAL 24 HOUR
                ORDER BY CASE WHEN state='SETTLED' THEN 0 ELSE 1 END, started_at LIMIT 25
                """, (row, index) -> new Orphan(row.getLong(1), UUID.fromString(row.getString(2)),
                row.getString(3), row.getString(4)));
        for (Orphan orphan : affected) {
            if (System.nanoTime() >= deadline) {
                return;
            }
            if (!orphan.state().equals("SETTLED")) {
                reconcile(files, orphan.owner(), orphan.job(), orphan.key());
            }
            removeOrphan(files, orphan.owner(), orphan.job(), orphan.key());
        }
    }

    // 고유 키가 실제 저장소에서 확인되면 한 번만 보낸 PUT의 완료를 기록할 수 있다.
    void reconcile(DocumentFiles files, long owner, UUID job, String key) throws IOException {
        requireOwnedKey(job, key);
        files.reconcile(key, () -> complete(owner, key));
    }

    // 파일 잠금을 늦게 획득했더라도 지워진 쓰기 의도를 되살리지 않는다.
    private boolean pending(long owner, String key) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM document_translation_object_write
                WHERE owner_id=? AND object_key=? AND state='WRITING'
                """, Integer.class, owner, key);
        return count != null && count == 1;
    }

    // 성공이나 확정 실패는 탈퇴 접수 이후에도 기존 쓰기 기록에만 반영한다.
    private void complete(long owner, String key) {
        independent.executeWithoutResult(status -> jdbc.update("""
                UPDATE document_translation_object_write
                SET state='SETTLED', settled_at=UTC_TIMESTAMP(6)
                WHERE owner_id=? AND object_key=? AND state='WRITING'
                """, owner, key));
    }

    // DB나 큐에서 얻은 값도 원본 작업에 속한 비공개 키 형식을 만족해야 실제 저장소에 적용할 수 있다.
    static void requireOwnedKey(UUID job, String key) {
        if (key == null || !KEY.matcher(key).matches()
                || !(key.equals("source-" + job) || key.startsWith("result-" + job + "-")
                        || key.startsWith("batch-" + job + "-"))) {
            throw new IllegalStateException("Document object does not belong to its job");
        }
    }

    private record Orphan(long owner, UUID job, String key, String state) {
    }
}
