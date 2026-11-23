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
        boolean multipart = files instanceof DocumentFiles.PrivateBucket;
        if (!reserve(owner, job, key, multipart ? "PREPARING" : "WRITING", permitted)) {
            return false;
        }
        if (files instanceof DocumentFiles.PrivateBucket bucket) {
            return storeMultipart(bucket, owner, job, key, source, permitted);
        }

        // 로컬에서는 OS 잠금이 작업 종료 또는 재시작 이후의 쓰기를 차단한다.
        try {
            files.put(key, source, () -> pending(owner, key));
        } catch (DocumentFiles.UnconfirmedWrite unconfirmed) {
            throw unconfirmed;
        } catch (IOException settledFailure) {
            complete(owner, job, key, null);
            throw settledFailure;
        }
        return complete(owner, job, key, null);
    }

    // 저장소에 접근하기 전에 권한과 작업 상태를 확인하고 정리할 키를 먼저 커밋한다.
    private boolean reserve(long owner, UUID job, String key, String state, BooleanSupplier permitted) {
        Boolean reserved = independent.execute(status -> {
            if (!accounts.lockActive(owner) || !permitted.getAsBoolean()) {
                return false;
            }
            jdbc.update("""
                    INSERT INTO document_translation_object_write (object_key, owner_id, job_id, state, started_at)
                    VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6))
                    """, key, owner, job.toString(), state);
            return true;
        });
        return Boolean.TRUE.equals(reserved);
    }

    // 업로드 ID를 보존하고 권한을 다시 확인한 뒤에만 사용자 문서 바이트를 전송한다.
    private boolean storeMultipart(DocumentFiles.PrivateBucket bucket, long owner, UUID job, String key,
            Path source, BooleanSupplier permitted) throws IOException {
        String uploadId;
        try {
            uploadId = bucket.createMultipart(key);
        } catch (IOException unavailable) {
            forgetPreparation(owner, job, key);
            throw unavailable;
        }

        boolean attached;
        try {
            attached = attachUpload(owner, job, key, uploadId, permitted);
        } catch (RuntimeException unavailable) {
            abortEmptyUpload(bucket, key, uploadId);
            throw unavailable;
        }
        if (!attached) {
            abortEmptyUpload(bucket, key, uploadId);
            forgetPreparation(owner, job, key);
            return false;
        }

        // 데이터 전송 이후에 오류가 발생하면 복구에 필요한 업로드 ID와 상태를 보존한다.
        bucket.writeMultipart(key, uploadId, source);
        return complete(owner, job, key, uploadId);
    }

    // PREPARING이 이미 정리됐거나 탈퇴·리스 만료로 권한이 닫혔으면 전송을 거부한다.
    private boolean attachUpload(long owner, UUID job, String key, String uploadId, BooleanSupplier permitted) {
        return Boolean.TRUE.equals(independent.execute(status -> accounts.lockActive(owner)
                && permitted.getAsBoolean()
                && jdbc.update("""
                        UPDATE document_translation_object_write
                        SET state='WRITING', upload_id=?
                        WHERE owner_id=? AND job_id=? AND object_key=? AND state='PREPARING' AND upload_id IS NULL
                        """, uploadId, owner, job.toString(), key) == 1));
    }

    // PREPARING에는 문서 바이트가 없다. 오래된 응답이 이 키에 연결되지 않도록 조건부로 지운다.
    private void forgetPreparation(long owner, UUID job, String key) {
        jdbc.update("""
                DELETE FROM document_translation_object_write
                WHERE owner_id=? AND job_id=? AND object_key=? AND state='PREPARING' AND upload_id IS NULL
                """, owner, job.toString(), key);
    }

    // 연결에 실패한 빈 업로드를 닫는다. 응답을 잃은 빈 세션에는 저장소 수명 주기 규칙이 적용된다.
    private void abortEmptyUpload(DocumentFiles.PrivateBucket bucket, String key, String uploadId) {
        try {
            bucket.abortMultipart(key, uploadId);
        } catch (IOException unavailable) {
            // 이 세션에는 문서 바이트를 전송하지 않았다.
        }
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
        WriteState write = writeState(owner, job, key);
        if (write == null || !write.state().equals("SETTLED")) {
            return;
        }
        if (write.uploadId() != null) {
            reconcileMultipart(files, owner, job, key, write);
        } else {
            files.delete(key);
            forget(owner, job, key);
        }
    }

    // 작업에 연결되지 않은 오래된 파일을 제한된 수로 다시 확인한다.
    void cleanOldOrphans(DocumentFiles files) throws IOException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        var affected = jdbc.query("""
                SELECT owner_id,job_id,object_key FROM document_translation_object_write
                WHERE state IN ('PREPARING','WRITING','SETTLED','RECONCILING')
                    AND started_at < UTC_TIMESTAMP(6) - INTERVAL 24 HOUR
                ORDER BY CASE WHEN state='WRITING' AND upload_id IS NULL THEN 1 ELSE 0 END, started_at LIMIT 25
                """, (row, index) -> new Orphan(row.getLong(1), UUID.fromString(row.getString(2)),
                row.getString(3)));
        IOException deferred = null;
        for (Orphan orphan : affected) {
            if (System.nanoTime() >= deadline) {
                break;
            }
            try {
                reconcile(files, orphan.owner(), orphan.job(), orphan.key());
                removeOrphan(files, orphan.owner(), orphan.job(), orphan.key());
            } catch (IOException unavailable) {
                // 한 키의 복구 실패로 같은 작업 예산에 포함된 나머지 키를 건너뛰지 않는다.
                deferred = unavailable;
            }
        }
        if (deferred != null) {
            throw deferred;
        }
    }

    // 계정 삭제 또는 오래된 고아 처리에서 입증 가능한 상태만 종료한다.
    void reconcile(DocumentFiles files, long owner, UUID job, String key) throws IOException {
        requireOwnedKey(job, key);
        WriteState write = writeState(owner, job, key);
        if (write == null) {
            return;
        }
        if (write.state().equals("PREPARING")) {
            forgetPreparation(owner, job, key);
        } else if (write.uploadId() != null) {
            reconcileMultipart(files, owner, job, key, write);
        } else if (write.state().equals("WRITING") && !(files instanceof DocumentFiles.PrivateBucket)) {
            // ID가 없는 과거 버킷 PUT에는 안전한 완료 근거가 없다. 로컬 잠금 복구만 허용한다.
            files.reconcile(key, () -> complete(owner, job, key, null));
        }
    }

    // 저장 결과를 게시할 수 없도록 먼저 닫은 다음 제공자가 완료를 확인한 동일한 ID만 지운다.
    private void reconcileMultipart(DocumentFiles files, long owner, UUID job, String key, WriteState write)
            throws IOException {
        if (!(files instanceof DocumentFiles.PrivateBucket bucket)) {
            throw new IOException("Document multipart cleanup requires its object store");
        }
        String uploadId = write.uploadId();
        if (!write.state().equals("RECONCILING") && jdbc.update("""
                UPDATE document_translation_object_write SET state='RECONCILING'
                WHERE owner_id=? AND job_id=? AND object_key=? AND upload_id=? AND state IN ('WRITING','SETTLED')
                """, owner, job.toString(), key, uploadId) != 1) {
            return;
        }

        bucket.reconcileMultipart(key, uploadId);
        jdbc.update("""
                DELETE FROM document_translation_object_write
                WHERE owner_id=? AND job_id=? AND object_key=? AND upload_id=? AND state='RECONCILING'
                """, owner, job.toString(), key, uploadId);
    }

    // 소유자·작업·키가 모두 일치하는 현재 기록을 읽는다.
    private WriteState writeState(long owner, UUID job, String key) {
        return jdbc.query("""
                SELECT state,upload_id FROM document_translation_object_write
                WHERE owner_id=? AND job_id=? AND object_key=?
                """, (row, index) -> new WriteState(row.getString(1), row.getString(2)),
                owner, job.toString(), key).stream().findFirst().orElse(null);
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
    private boolean complete(long owner, UUID job, String key, String uploadId) {
        return Boolean.TRUE.equals(independent.execute(status -> jdbc.update("""
                UPDATE document_translation_object_write
                SET state='SETTLED', settled_at=UTC_TIMESTAMP(6)
                WHERE owner_id=? AND job_id=? AND object_key=? AND upload_id <=> ? AND state='WRITING'
                """, owner, job.toString(), key, uploadId) == 1));
    }

    // DB나 큐에서 얻은 값도 원본 작업에 속한 비공개 키 형식을 만족해야 실제 저장소에 적용할 수 있다.
    static void requireOwnedKey(UUID job, String key) {
        if (key == null || !KEY.matcher(key).matches()
                || !(key.equals("source-" + job) || key.startsWith("result-" + job + "-")
                        || key.startsWith("batch-" + job + "-"))) {
            throw new IllegalStateException("Document object does not belong to its job");
        }
    }

    private record Orphan(long owner, UUID job, String key) {
    }

    private record WriteState(String state, String uploadId) {
    }
}
