package jp.co.translacat.domain.imagetranslation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.deletion.AccountDataLifecycleAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.*;

@Repository
@ConditionalOnProperty(prefix = "translacat.image-translation", name = "asset-secret")
class ImageTranslationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final AccountDataLifecycleAccess accountLifecycle;

    // 회차 잠금 뒤에도 새 멱등 기록을 읽을 수 있도록 읽기 격리를 정한다.
    ImageTranslationStore(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactionManager,
            AccountDataLifecycleAccess accountLifecycle) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.accountLifecycle = accountLifecycle;
    }

    // 유효한 소유권 검사, 상태 변경과 수락 기록을 한 DB 트랜잭션으로 실행한다.
    <T> T transaction(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    // 기존 계정 수명주기 잠금 뒤에 이 모듈의 소유자 잠금을 잡는다.
    boolean lockActiveOwner(long owner) {
        return accountLifecycle.lockActive(owner) && !lockOwner(owner);
    }

    // 사용자의 첫 동시 요청도 같은 한도 행에서 직렬화한다. 삭제된 행은 상태를 보존한다.
    boolean lockOwner(long owner) {
        jdbc.update("""
                INSERT INTO image_translation_owner (owner_id, removed) VALUES (?, FALSE)
                ON DUPLICATE KEY UPDATE owner_id = owner_id
                """, owner);
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT removed FROM image_translation_owner WHERE owner_id = ? FOR UPDATE
                """, Boolean.class, owner));
    }

    // 동작 중이던 로그인 요청과 워커가 이후 자료를 발급하거나 저장하지 못하게 막는다.
    void markOwnerRemoved(long owner) {
        jdbc.update("UPDATE image_translation_owner SET removed = TRUE WHERE owner_id = ?", owner);
    }

    // 작업 행을 잠그기 전에 수명주기와 소유자 잠금을 획득한다.
    Job lockWritableJob(UUID id) {
        Long owner = ownerOf(id);
        if (owner == null || !lockActiveOwner(owner)) {
            return null;
        }
        return find(id, true);
    }

    // 공개 직전 계정 수명주기를 확인하며 읽기에는 모듈 행 생성을 요구하지 않는다.
    Job findReadableJob(UUID id, Long requestedOwner) {
        return transaction(() -> {
            Long owner = requestedOwner == null ? ownerOf(id) : requestedOwner;
            if (owner == null || !accountLifecycle.lockActive(owner)) {
                return null;
            }
            return find(id, false);
        });
    }

    // 큰 작업 스냅샷을 읽기 전에 잠금 순서를 정할 수 있도록 소유자 인덱스만 조회한다.
    private Long ownerOf(UUID id) {
        return jdbc.query("SELECT owner_id FROM image_translation_job WHERE job_id = ?",
                (row, index) -> row.getLong(1), id.toString()).stream().findFirst().orElse(null);
    }

    // 계정 데이터 삭제는 이 모듈에 등록된 작업 ID만 정리한다.
    List<UUID> ownedJobIds(long owner) {
        return ids("SELECT job_id FROM image_translation_job WHERE owner_id = ? ORDER BY job_id", owner);
    }

    // 제거 요청을 받은 계정의 멱등 키와 요청 지문을 영구적으로 제거한다.
    void deleteOwnedRequests(long owner) {
        jdbc.update("DELETE FROM image_translation_request WHERE owner_id = ?", owner);
    }

    // 마이그레이션 준비가 확인되기 전에는 삭제 완료를 외부 계약에 반환하지 않는다.
    void requireCleanupSchema() {
        jdbc.queryForList("SELECT owner_id, removed FROM image_translation_owner WHERE 1 = 0");
        jdbc.queryForList("SELECT job_id, snapshot_json FROM image_translation_job WHERE 1 = 0");
        jdbc.queryForList("SELECT owner_id, job_id FROM image_translation_request WHERE 1 = 0");
    }

    // 한 회차를 읽고, 변경 요청일 때는 커밋까지 해당 행을 잠근다.
    Job find(UUID id, boolean forUpdate) {
        String sql = "SELECT snapshot_json FROM image_translation_job WHERE job_id = ?";
        if (forUpdate) {
            sql += " FOR UPDATE";
        }
        return jdbc.query(sql, (rs, row) -> decode(rs.getString(1)), id.toString()).stream().findFirst().orElse(null);
    }

    // 새 회차를 저장한다. 페이지 정보 및 인덱스 열은 같은 트랜잭션으로 다룬다.
    void insert(Job job, long now) {
        jdbc.update("""
                INSERT INTO image_translation_job
                    (job_id, owner_id, work_status, touched_ms, expires_ms, cleanup_due_ms, snapshot_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, job.id.toString(), job.owner, job.status.name(), now, job.expiresAt, job.cleanupDue, encode(job));
    }

    // 잠근 회차의 스냅샷과 워커 탐색 인덱스를 동시에 갱신한다.
    void save(Job job, long now) {
        jdbc.update("""
                UPDATE image_translation_job
                SET work_status = ?, touched_ms = ?, expires_ms = ?, cleanup_due_ms = ?, snapshot_json = ?
                WHERE job_id = ?
                """, job.status.name(), now, job.expiresAt, job.cleanupDue, encode(job), job.id.toString());
    }

    // 오래 기다린 회차부터 제한된 후보만 반환한다.
    List<UUID> workIds(long now) {
        return ids("""
                SELECT job_id FROM image_translation_job
                WHERE work_status IN ('PREPARING', 'QUEUED', 'RUNNING') AND expires_ms > ?
                ORDER BY touched_ms, job_id LIMIT 32
                """, now);
    }

    // 아직 만료 처리가 되지 않은 회차를 고른다.
    List<UUID> expiredIds(long now) {
        return ids("""
                SELECT job_id FROM image_translation_job
                WHERE expires_ms <= ? AND work_status <> 'EXPIRED'
                ORDER BY expires_ms LIMIT 32
                """, now);
    }

    // 자산 정리 또는 만료 이력 파기가 예정된 회차를 고른다.
    List<UUID> cleanupIds(long now) {
        return ids("""
                SELECT job_id FROM image_translation_job
                WHERE cleanup_due_ms <= ? AND work_status = 'EXPIRED'
                ORDER BY cleanup_due_ms LIMIT 16
                """, now);
    }

    // 만료 이력을 없애고, FK에 의해 관련 요청 키도 제거한다.
    void delete(UUID id) {
        jdbc.update("DELETE FROM image_translation_job WHERE job_id = ?", id.toString());
    }

    // 계정과 연산의 조합에서 이미 수락한 키를 찾는다.
    Accepted accepted(long owner, Operation operation, String keyHash) {
        return jdbc.query("""
                SELECT job_id, fingerprint FROM image_translation_request
                WHERE owner_id = ? AND operation_name = ? AND request_key_hash = ?
                """, (row, index) -> new Accepted(UUID.fromString(row.getString(1)), row.getString(2)),
                owner, operation.name(), keyHash).stream().findFirst().orElse(null);
    }

    // 다음 동일 요청을 재접수하지 않도록 변경과 함께 기록한다.
    void accept(long owner, Operation operation, String keyHash, String fingerprint, UUID jobId) {
        jdbc.update("""
                INSERT INTO image_translation_request
                    (owner_id, operation_name, request_key_hash, fingerprint, job_id)
                VALUES (?, ?, ?, ?, ?)
                """, owner, operation.name(), keyHash, fingerprint, jobId.toString());
    }

    // 소유자 잠금 아래에서만 조회해 한도 검사와 접수를 원자적으로 처리한다.
    int openCount(long owner, long now) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM image_translation_job WHERE owner_id = ? AND expires_ms > ?
                AND work_status IN ('AWAITING_UPLOAD', 'PREPARING', 'READY', 'QUEUED', 'RUNNING')
                """, Integer.class, owner, now);
        return count == null ? 0 : count;
    }

    // 내부 UUID만 읽어 JDBC 표현을 호출 범위 밖으로 전달하지 않는다.
    private List<UUID> ids(String query, long time) {
        return jdbc.query(query, (rs, row) -> UUID.fromString(rs.getString(1)), time);
    }

    // 현재 저장 스키마에서 지원하는 회차 스냅샷을 복원한다.
    private Job decode(String json) {
        try {
            return mapper.readValue(json, Job.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Invalid image translation snapshot", error);
        }
    }

    // DB에 저장하는 형식은 외부 HTTP 뷰와 분리한다.
    private String encode(Job job) {
        try {
            return mapper.writeValueAsString(job);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Invalid image translation job", error);
        }
    }

    record Accepted(UUID jobId, String fingerprint) {}
}
