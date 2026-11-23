package jp.co.translacat.domain.documenttranslation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 문서 작업 테이블과 배치 체크포인트의 트랜잭션을 관리한다. */
final class DocumentJobRepository {
    private static final String TABLE = "document_translation_job";
    private static final TypeReference<List<DocumentJob.Warning>> WARNINGS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;
    private final DocumentObjectWrites writes;

    // 문서용 SQL 어댑터를 기존 데이터 소스와 트랜잭션 관리자에 연결한다.
    DocumentJobRepository(JdbcTemplate jdbc, TransactionTemplate transaction, ObjectMapper mapper,
            DocumentObjectWrites writes) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.mapper = mapper;
        this.writes = writes;
    }

    // 인증 주체와 키로 접수 재생 대상을 조회한다.
    Optional<DocumentJob> findByKey(long ownerId, UUID key) {
        return writes.whenActive(ownerId, Optional.empty(),
                () -> jdbc.query("SELECT * FROM " + TABLE + " WHERE owner_id = ? AND idempotency_key = ?",
                        this::read, ownerId, key.toString()).stream().findFirst());
    }

    // 공용 인증에서 확인한 ID로 조회 범위를 제한한다.
    Optional<DocumentJob> findOwned(long ownerId, UUID id) {
        return writes.whenActive(ownerId, Optional.empty(), () -> loadOwned(ownerId, id));
    }

    // 작업 클레임 중에는 계정 잠금을 새로 획득하지 않아 계정 삭제와 잠금 순서가 엇갈리지 않는다.
    private Optional<DocumentJob> loadOwned(long ownerId, UUID id) {
        return jdbc.query("SELECT * FROM " + TABLE + " WHERE owner_id = ? AND id = ?",
                this::read, ownerId, id.toString()).stream().findFirst();
    }

    // DB 유일 제약으로 멱등 키와 활성 소유자 경합을 해결한다.
    boolean insert(DocumentJob job) {
        return writes.whenActive(job.ownerId(), false, () -> {
            jdbc.update("""
                    INSERT INTO document_translation_job
                        (id, owner_id, retry_of, idempotency_key, request_hash, format, file_name, source_language,
                         target_language, input_key, input_sha256, input_bytes, warnings, created_at, updated_at,
                         expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]', ?, ?, ?)
                    """, job.id().toString(), job.ownerId(), value(job.retryOf()), job.idempotencyKey().toString(),
                    job.requestHash(), job.format(), job.fileName(), job.sourceLanguage(), job.targetLanguage(),
                    job.inputKey(), job.inputHash(), job.inputBytes(), time(job.createdAt()),
                    time(job.createdAt()), time(job.expiresAt()));
            if (job.retryOf() == null) {
                writes.link(job.ownerId(), job.id(), job.inputKey());
            }
            return true;
        });
    }

    // 아직 실행하지 않은 작업과 복구 가능한 만료 리스에서 먼저 접수한 항목을 가져온다.
    Optional<DocumentJob> claim(Instant now) {
        return transaction.execute(status -> {
            var candidates = jdbc.query("""
                    SELECT * FROM document_translation_job
                    WHERE expires_at > ?
                        AND created_at > ?
                        AND (status = 'QUEUED' OR (
                            status IN ('VALIDATING', 'TRANSLATING', 'REPACKING')
                            AND lease_until < ? AND recovery_count = 0))
                    ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, this::read, time(now), time(now.minusSeconds(180)), time(now));
            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            var job = candidates.getFirst();
            UUID lease = UUID.randomUUID();
            int recoveries = job.recoveryCount() + (job.state() == DocumentJob.State.QUEUED ? 0 : 1);
            jdbc.update("""
                    UPDATE document_translation_job SET status = 'VALIDATING', lease_token = ?, lease_until = ?,
                        recovery_count = ?, updated_at = ?
                    WHERE id = ?
                    """, lease.toString(), time(now.plusSeconds(30)), recoveries, time(now), job.id().toString());
            return loadOwned(job.ownerId(), job.id());
        });
    }

    // 늦게 돌아온 작업이 완료 상태 또는 다른 리스를 덮어쓰지 않도록 토큰을 대조한다.
    boolean progress(DocumentJob job, DocumentJob.State stage, int total, int completed, String model, Instant now) {
        return writes.whenActive(job.ownerId(), false, () -> jdbc.update("""
                UPDATE document_translation_job
                SET status = ?, total_segments = ?, completed_segments = GREATEST(completed_segments, ?),
                    model_revision = ?, updated_at = ?
                WHERE id = ? AND lease_token = ? AND lease_until >= ?
                    AND status IN ('VALIDATING', 'TRANSLATING', 'REPACKING')
                """, stage.name(), total, completed, model, time(now), job.id().toString(),
                value(job.leaseToken()), time(now)) == 1);
    }

    // 처리 시간이 긴 외부 호출 중에도 살아 있는 워커의 리스를 유지한다.
    boolean renew(DocumentJob job, Instant now) {
        return writes.whenActive(job.ownerId(), false, () -> jdbc.update("""
                UPDATE document_translation_job SET lease_until = ?
                WHERE id = ? AND lease_token = ? AND lease_until >= ?
                    AND status IN ('VALIDATING', 'TRANSLATING', 'REPACKING')
                """, time(now.plusSeconds(30)), job.id().toString(), value(job.leaseToken()), time(now)) == 1);
    }

    // 외부 번역 요청 직전에 취소 여부와 리스 유효 시간을 함께 확인한다.
    boolean canTranslate(DocumentJob job, Instant now) {
        return canRun(job, DocumentJob.State.TRANSLATING, now);
    }

    // 원본을 열거나 외부 호출을 승인하기 전에 계정과 같은 시도의 리스를 확인한다.
    boolean canRun(DocumentJob job, DocumentJob.State stage, Instant now) {
        return writes.whenActive(job.ownerId(), false, () -> hasLease(job, stage, now));
    }

    // 호출자가 이미 활성 계정 행을 잠근 저장 경계에서 현재 단계만 확인한다.
    boolean hasLease(DocumentJob job, DocumentJob.State stage, Instant now) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM document_translation_job
                WHERE id = ? AND owner_id = ? AND lease_token = ? AND status = ? AND lease_until >= ?
                """, Integer.class, job.id().toString(), job.ownerId(), value(job.leaseToken()), stage.name(),
                time(now));
        return count != null && count == 1;
    }

    // 파일 저장이 끝난 뒤 REPACKING과 현재 리스가 모두 유효할 때만 다운로드를 공개한다.
    boolean succeed(DocumentJob job, String key, String hash, long bytes,
            List<DocumentJob.Warning> warnings, Instant now) {
        return writes.whenActive(job.ownerId(), false, () -> {
            boolean published = jdbc.update("""
                UPDATE document_translation_job SET status = 'SUCCEEDED', output_key = ?, output_sha256 = ?,
                    output_bytes = ?, warnings = ?, lease_token = NULL, lease_until = NULL,
                    updated_at = ?, finished_at = ?
                WHERE id = ? AND lease_token = ? AND status = 'REPACKING' AND lease_until >= ? AND expires_at > ?
                """, key, hash, bytes, json(warnings), time(now), time(now), job.id().toString(),
                    value(job.leaseToken()), time(now), time(now)) == 1;
            if (published) {
                writes.link(job.ownerId(), job.id(), key);
            }
            return published;
        });
    }

    // 진행 중인 단계에서만 실패를 확정한다.
    void fail(DocumentJob job, String errorCode, Instant now) {
        writes.whenActive(job.ownerId(), 0, () -> jdbc.update("""
                UPDATE document_translation_job SET status = 'FAILED', error_code = ?, lease_token = NULL,
                    lease_until = NULL, updated_at = ?, finished_at = ?
                WHERE id = ? AND lease_token = ? AND lease_until >= ?
                    AND status IN ('VALIDATING', 'TRANSLATING', 'REPACKING')
                """, errorCode, time(now), time(now), job.id().toString(), value(job.leaseToken()), time(now)));
    }

    // 첫 상태 전이를 보존하면서 대기 중 작업은 즉시, 실행 중은 취소 요청으로 옮긴다.
    void requestCancel(long ownerId, UUID id, Instant now) {
        writes.whenActive(ownerId, 0, () -> jdbc.update("""
                UPDATE document_translation_job
                SET finished_at = CASE WHEN status = 'QUEUED' THEN ? ELSE finished_at END,
                    status = CASE WHEN status = 'QUEUED' THEN 'CANCELLED' ELSE 'CANCELLING' END,
                    updated_at = ?
                WHERE owner_id = ? AND id = ? AND expires_at > ?
                    AND status IN ('QUEUED', 'VALIDATING', 'TRANSLATING', 'REPACKING')
                """, time(now), time(now), ownerId, id.toString(), time(now)));
    }

    // 현재 워커가 취소 경계를 지난 작업을 정리한다.
    void completeCancel(DocumentJob job, Instant now) {
        writes.whenActive(job.ownerId(), 0, () -> jdbc.update("""
                UPDATE document_translation_job SET status = 'CANCELLED', lease_token = NULL, lease_until = NULL,
                    updated_at = ?, finished_at = ?
                WHERE id = ? AND lease_token = ? AND status = 'CANCELLING'
                """, time(now), time(now), job.id().toString(), value(job.leaseToken())));
    }

    // 중단한 워커를 기다리지 않고 임대 만료 취소와 전체 마감을 확정한다.
    void sweep(Instant now) {
        jdbc.update("""
                UPDATE document_translation_job SET status = 'CANCELLED', lease_token = NULL, lease_until = NULL,
                    updated_at = ?, finished_at = ?
                WHERE status = 'CANCELLING' AND lease_until < ?
                """, time(now), time(now), time(now));
        jdbc.update("""
                UPDATE document_translation_job SET status = 'FAILED',
                    error_code = CASE WHEN created_at <= ? THEN 'TRANSLATION_TIMEOUT' ELSE 'WORKER_UNAVAILABLE' END,
                    lease_token = NULL, lease_until = NULL, updated_at = ?, finished_at = ?
                WHERE status IN ('QUEUED', 'VALIDATING', 'TRANSLATING', 'REPACKING')
                    AND (created_at <= ? OR lease_until < ? AND recovery_count >= 1)
                """, time(now.minusSeconds(180)), time(now), time(now), time(now.minusSeconds(180)), time(now));
    }

    // 같은 입력 지문으로 전에 저장한 배치를 찾는다.
    Optional<Checkpoint> checkpoint(UUID id, int number, String hash) {
        return jdbc.query("""
                SELECT batch_no, result_key FROM document_translation_batch
                WHERE job_id = ? AND batch_no = ? AND input_hash = ?
                """, (rs, row) -> new Checkpoint(rs.getInt(1), rs.getString(2)), id.toString(), number, hash)
                .stream().findFirst();
    }

    // 검증한 배치 결과만 현재 리스에서 한 번 기록하도록 DB에서 조건을 확인한다.
    boolean saveCheckpoint(DocumentJob job, int number, String hash, String key, Instant now) {
        return writes.whenActive(job.ownerId(), false, () -> {
            boolean saved = jdbc.update("""
                INSERT IGNORE INTO document_translation_batch (job_id, batch_no, input_hash, result_key, completed_at)
                SELECT id, ?, ?, ?, ? FROM document_translation_job
                WHERE id = ? AND lease_token = ? AND status = 'TRANSLATING' AND lease_until >= ?
                    """, number, hash, key, time(now), job.id().toString(), value(job.leaseToken()), time(now)) == 1;
            if (saved) {
                writes.link(job.ownerId(), job.id(), key);
            }
            return saved;
        });
    }

    // 필요한 경우 다시 읽을 수 있게 파일을 지우기 전 작업과 배치 키를 조회한다.
    List<DocumentJob> expiredWithFiles(Instant now) {
        return jdbc.query("""
                SELECT * FROM document_translation_job
                WHERE expires_at <= ? AND (input_key IS NOT NULL OR output_key IS NOT NULL)
                ORDER BY expires_at LIMIT 50
                """, this::read, time(now));
    }

    // TTL 정리에서 작업이 보유한 체크포인트 객체의 키를 가져온다.
    List<String> checkpointKeys(UUID id) {
        return jdbc.queryForList("SELECT result_key FROM document_translation_batch WHERE job_id = ?",
                String.class, id.toString());
    }

    // 객체 삭제 후 조회할 수 없는 키를 비운다.
    void clearExpired(DocumentJob job, Instant now) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM document_translation_batch WHERE job_id = ?", job.id().toString());
            int cleared = jdbc.update("""
                    UPDATE document_translation_job SET status = 'EXPIRED', input_key = NULL, output_key = NULL,
                        lease_token = NULL, lease_until = NULL, error_code = NULL, updated_at = ?
                    WHERE id = ? AND expires_at <= ?
                    """, time(now), job.id().toString(), time(now));
            if (cleared == 1) {
                writes.clearExpiredSource(job.ownerId(), job.inputKey());
            }
        });
    }

    // 파일 정리가 끝난 작업만 접수 후 7일에 제거한다.
    void removeOldMetadata(Instant now) {
        jdbc.update("""
                DELETE FROM document_translation_job
                WHERE created_at <= ? AND status = 'EXPIRED' AND input_key IS NULL AND output_key IS NULL LIMIT 200
                """, time(now.minusSeconds(7L * 24 * 60 * 60)));
    }

    // 저장된 작업을 문서 모듈의 값으로 변환한다.
    private DocumentJob read(ResultSet row, int rowNumber) throws SQLException {
        try {
            return new DocumentJob(uuid(row, "id"), row.getLong("owner_id"), uuid(row, "retry_of"),
                    uuid(row, "idempotency_key"), row.getString("request_hash"), row.getString("format"),
                    row.getString("file_name"), row.getString("source_language"), row.getString("target_language"),
                    DocumentJob.State.valueOf(row.getString("status")), row.getString("input_key"),
                    row.getString("input_sha256"), row.getLong("input_bytes"), row.getString("output_key"),
                    row.getObject("output_bytes", Long.class), row.getString("output_sha256"),
                    row.getObject("total_segments", Integer.class), row.getInt("completed_segments"),
                    mapper.readValue(row.getString("warnings"), WARNINGS), row.getString("error_code"),
                    row.getString("model_revision"), uuid(row, "lease_token"), row.getInt("recovery_count"),
                    instant(row, "created_at"), instant(row, "expires_at"));
        } catch (JsonProcessingException malformed) {
            throw new SQLException("Invalid stored document warnings");
        }
    }

    // 경고 구조를 DB JSON 타입으로 전달한다.
    private String json(List<DocumentJob.Warning> warnings) {
        try {
            return mapper.writeValueAsString(warnings);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Cannot serialize document warnings");
        }
    }

    // 저장된 NULL을 유지하면서 UUID를 읽는다.
    private static UUID uuid(ResultSet row, String column) throws SQLException {
        String stored = row.getString(column);
        return stored == null ? null : UUID.fromString(stored);
    }

    // 시간대 없이 저장된 문서 작업 시간을 UTC로 해석한다.
    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    // UTC 값으로 JDBC DATETIME 매개변수를 만든다.
    private static LocalDateTime time(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    // UUID의 표준 저장 표현을 만든다.
    private static String value(UUID id) {
        return id == null ? null : id.toString();
    }

    record Checkpoint(int number, String key) {}
}
