package jp.co.translacat.domain.documenttranslation;

import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import jp.co.translacat.domain.user.deletion.AccountDeletionDomainCleanup;
import jp.co.translacat.domain.user.deletion.AccountDeletionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 계정 삭제에서 문서 작업의 공개를 차단하고 도메인 저장소의 객체와 기록을 삭제한다. */
@Component
final class DocumentAccountCleanup implements AccountDeletionDomainCleanup {
    private static final Set<String> TABLES = Set.of(
            "document_translation_job", "document_translation_batch", "document_translation_object_write");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AccountDataLifecycleAccess accounts;
    private final DocumentObjectWrites writes;
    private final DocumentStorageAccess storage;
    private final boolean enabled;

    // 문서 API가 비활성화된 상태에서도 계정 삭제 모듈은 유지한다.
    DocumentAccountCleanup(JdbcTemplate jdbc, PlatformTransactionManager manager, AccountDataLifecycleAccess accounts,
            DocumentObjectWrites writes, DocumentStorageAccess storage,
            @Value("${translacat.document-translation.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        transactions = new TransactionTemplate(manager);
        this.accounts = accounts;
        this.writes = writes;
        this.storage = storage;
        this.enabled = enabled;
    }

    @Override
    public String domain() {
        return "DOCUMENT_TRANSLATION";
    }

    @Override
    public void requireReady() {
        if (!schemaReady()) {
            return;
        }

        // 일부만 적용한 마이그레이션이나 파일이 있는데 저장소를 제거한 경우 계정 삭제 접수를 보류한다.
        jdbc.queryForList("""
                SELECT owner_id,id,input_key,output_key,status,lease_token FROM document_translation_job WHERE 1=0
                """);
        jdbc.queryForList("SELECT job_id,result_key FROM document_translation_batch WHERE 1=0");
        jdbc.queryForList("SELECT owner_id,job_id,object_key,state FROM document_translation_object_write WHERE 1=0");
        if ((enabled || hasObjects()) && storage.configured().isEmpty()) {
            throw new IllegalStateException("Document cleanup object store is not configured");
        }
    }

    @Override
    public boolean clean(AccountDeletionStore.Operation operation) {
        if (!schemaReady()) {
            return true;
        }

        long owner = operation.userId();
        // 이미 시작된 작업의 임대를 먼저 차단한다. 그 이후에는 기존 객체만 정리 대상으로 남는다.
        transactions.executeWithoutResult(status -> {
            requireDeletion(owner);
            jdbc.update("""
                    UPDATE document_translation_job
                    SET status='CANCELLED', lease_token=NULL, lease_until=NULL,
                        updated_at=UTC_TIMESTAMP(6), finished_at=UTC_TIMESTAMP(6)
                    WHERE owner_id=? AND status IN ('QUEUED','VALIDATING','TRANSLATING','REPACKING','CANCELLING')
                    """, owner);
        });

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        // 파일을 여는 동안 DB 잠금을 보유하지 않는다. 확인된 삭제만 다음 재시도에서 제외한다.
        try {
            var files = storage.configured();
            List<OwnedObject> confirmed = confirmedObjects(owner);
            List<OwnedObject> unconfirmed = unconfirmedObjects(owner);
            if (files.isEmpty() && (!confirmed.isEmpty() || !unconfirmed.isEmpty())) {
                return false;
            }
            if (files.isPresent() && !deleteKnown(owner, files.get(), confirmed, deadline)) {
                return false;
            }
            if (files.isPresent() && !reconcile(owner, files.get(), unconfirmed, deadline)) {
                return false;
            }
        } catch (IOException unavailable) {
            return false;
        }

        // 객체가 남아 있으면 job 행을 지우지 않아 다음 worker에서도 계정 소유 키를 확인할 수 있다.
        return Boolean.TRUE.equals(transactions.execute(status -> {
            requireDeletion(owner);
            if (!confirmedObjects(owner).isEmpty() || !unconfirmedObjects(owner).isEmpty()) {
                return false;
            }
            jdbc.update("DELETE FROM document_translation_job WHERE owner_id=?", owner);
            return true;
        }));
    }

    // 비활성 상태에서 세 테이블이 모두 없는 경우만 문서 기능 배포 전 환경으로 본다.
    private boolean schemaReady() {
        Set<String> present = jdbc.execute((ConnectionCallback<Set<String>>) connection -> {
            Set<String> found = new HashSet<>();
            try (var tables = connection.getMetaData().getTables(
                         connection.getCatalog(), connection.getSchema(), "%", new String[] {"TABLE", "BASE TABLE"})) {
                while (tables.next()) {
                    String table = tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                    if (TABLES.contains(table)) {
                        found.add(table);
                    }
                }
            }
            return found;
        });
        if (present != null && present.containsAll(TABLES)) {
            return true;
        }
        if (!enabled && present != null && present.isEmpty()) {
            return false;
        }
        throw new IllegalStateException("Document cleanup schema is incomplete");
    }

    // 도메인 파일 저장이 사용된 적 있는지 확인하고 스위치 상태와 저장소 유무를 구분한다.
    private boolean hasObjects() {
        long rows = count("""
                SELECT COUNT(*) FROM document_translation_job WHERE input_key IS NOT NULL OR output_key IS NOT NULL
                """) + count("SELECT COUNT(*) FROM document_translation_batch")
                + count("SELECT COUNT(*) FROM document_translation_object_write");
        return rows != 0;
    }

    // 삭제가 접수되지 않은 계정의 파일은 정리 요청이 와도 지우지 않는다.
    private void requireDeletion(long owner) {
        if (!accounts.lockDeletionAccepted(owner)) {
            throw new IllegalStateException("Document cleanup requires an accepted account deletion");
        }
    }

    // 연결된 객체뿐 아니라 접수 실패·리스 상실로 아직 연결되지 않은 확정 파일도 조회한다.
    private List<OwnedObject> confirmedObjects(long owner) {
        return jdbc.query("""
                 SELECT input_key AS object_key, id AS job_id, 'SOURCE' AS origin
                 FROM document_translation_job WHERE owner_id=? AND input_key IS NOT NULL
                 UNION ALL
                 SELECT output_key, id, 'RESULT'
                 FROM document_translation_job WHERE owner_id=? AND output_key IS NOT NULL
                 UNION ALL
                 SELECT batch.result_key, batch.job_id, 'BATCH'
                 FROM document_translation_batch batch
                 JOIN document_translation_job job ON job.id=batch.job_id WHERE job.owner_id=?
                 UNION ALL
                 SELECT object_key, job_id, 'WRITE'
                 FROM document_translation_object_write WHERE owner_id=? AND state IN ('SETTLED','LINKED')
                 ORDER BY object_key LIMIT 40
                 """, (row, index) -> new OwnedObject(row.getString(1), UUID.fromString(row.getString(2)),
                row.getString(3)), owner, owner, owner, owner);
    }

    // 늦게 도착할 수도 있는 개별 원격 PUT은 완료 증거가 확인될 때까지 기록을 남긴다.
    private List<OwnedObject> unconfirmedObjects(long owner) {
        return jdbc.query("""
                SELECT object_key,job_id FROM document_translation_object_write
                WHERE owner_id=? AND state NOT IN ('SETTLED','LINKED') ORDER BY started_at LIMIT 20
                """, (row, index) -> new OwnedObject(row.getString(1), UUID.fromString(row.getString(2)), "WRITE"),
                owner);
    }

    // 확인된 쓰기는 짧은 작업 예산 안에서 하나씩 지우고 DB 참조를 제거한다.
    private boolean deleteKnown(long owner, DocumentFiles files, List<OwnedObject> objects, long deadline)
            throws IOException {
        Set<String> removed = new HashSet<>();
        for (OwnedObject object : objects) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            if (!removed.add(object.key())) {
                continue;
            }
            validateOwner(owner, object);
            files.delete(object.key());
            transactions.executeWithoutResult(status -> clearReferences(owner, object.key()));
        }
        return true;
    }

    // 네트워크 확인이 끝난 미확정 쓰기는 같은 고유 키를 다음 한 차례의 정리에 넘긴다.
    private boolean reconcile(long owner, DocumentFiles files, List<OwnedObject> objects, long deadline)
            throws IOException {
        for (OwnedObject object : objects) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            writes.reconcile(files, owner, object.job(), object.key());
        }
        return true;
    }

    // 재시도가 공유하는 원본은 어느 선행 작업의 것인지 확인한다.
    private void validateOwner(long owner, OwnedObject object) {
        if (object.origin().equals("SOURCE")) {
            if (!object.key().startsWith("source-")) {
                throw new IllegalStateException("Invalid document source key");
            }
            UUID original = UUID.fromString(object.key().substring("source-".length()));
            DocumentObjectWrites.requireOwnedKey(original, object.key());
            if (!writes.ownsLinkedSource(owner, original, object.key())
                    && count("SELECT COUNT(*) FROM document_translation_job WHERE owner_id=? AND id=?",
                            owner, original.toString()) == 0) {
                throw new IllegalStateException("Document source owner could not be confirmed");
            }
        } else {
            DocumentObjectWrites.requireOwnedKey(object.job(), object.key());
        }
    }

    // 확인된 객체만 참조를 제거하고 결과 메타데이터 삭제는 전체 정리 이후로 미룬다.
    private void clearReferences(long owner, String key) {
        jdbc.update("UPDATE document_translation_job SET input_key=NULL WHERE owner_id=? AND input_key=?", owner, key);
        jdbc.update("UPDATE document_translation_job SET output_key=NULL WHERE owner_id=? AND output_key=?",
                owner, key);
        jdbc.update("""
                DELETE FROM document_translation_batch WHERE result_key=?
                AND job_id IN (SELECT id FROM document_translation_job WHERE owner_id=?)
                """, key, owner);
        jdbc.update("""
                DELETE FROM document_translation_object_write
                WHERE owner_id=? AND object_key=? AND state IN ('SETTLED','LINKED')
                """, owner, key);
    }

    private long count(String query, Object... parameters) {
        Long count = jdbc.queryForObject(query, Long.class, parameters);
        return count == null ? 0 : count;
    }

    private record OwnedObject(String key, UUID job, String origin) {
    }
}
