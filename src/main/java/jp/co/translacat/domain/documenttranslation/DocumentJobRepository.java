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

/** 임시 문서 작업의 상태, 처리 임대와 일회 다운로드 권한을 DB에서 관리한다. */
final class DocumentJobRepository {
    private static final String TABLE = "document_translation_job";
    private static final String UNUSED_CONTENT_HASH = "0".repeat(64);
    private static final long RESULT_RETENTION_SECONDS = 120;
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

    // DB에는 임시 작업 식별자와 요청 메타데이터만 기록하고 원본명과 파일 지문은 보관하지 않는다.
    boolean insert(DocumentJob job) {
        if (job.retryOf() != null || !workspaceKey(job.id()).equals(job.inputKey())) {
            throw new IllegalArgumentException("An independent transient document workspace is required");
        }
        return writes.whenActive(job.ownerId(), false, () -> {
            jdbc.update("""
                    INSERT INTO document_translation_job
                        (id, owner_id, idempotency_key, request_hash, format, file_name, source_language,
                         target_language, input_key, input_sha256, input_bytes, warnings, created_at, updated_at,
                         expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]', ?, ?, ?)
                    """, job.id().toString(), job.ownerId(), job.idempotencyKey().toString(),
                    job.requestHash(), job.format(), "document." + job.format(), job.sourceLanguage(), job.targetLanguage(),
                    job.inputKey(), UNUSED_CONTENT_HASH, job.inputBytes(), time(job.createdAt()),
                    time(job.createdAt()), time(job.expiresAt()));
            return true;
        });
    }

    // 같은 임시 공간을 동시에 쓰지 않도록 아직 실행하지 않은 작업만 가져온다.
    Optional<DocumentJob> claim(Instant now) {
        return transaction.execute(status -> {
            var candidates = jdbc.query("""
                    SELECT * FROM document_translation_job
                    WHERE expires_at > ?
                        AND created_at > ?
                        AND input_key = CONCAT('ephemeral-', id)
                        AND status = 'QUEUED'
                    ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, this::read, time(now), time(now.minusSeconds(DocumentTranslationService.JOB_SECONDS)));
            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            var job = candidates.getFirst();
            UUID lease = UUID.randomUUID();
            jdbc.update("""
                    UPDATE document_translation_job SET status = 'VALIDATING', lease_token = ?, lease_until = ?,
                        updated_at = ?
                    WHERE id = ?
                    """, lease.toString(), time(now.plusSeconds(30)), time(now), job.id().toString());
            return loadOwned(job.ownerId(), job.id());
        });
    }

    // 늦게 돌아온 작업이 완료 상태 또는 다른 리스를 덮어쓰지 않도록 토큰을 대조한다.
    boolean progress(DocumentJob job, DocumentJob.State stage, int total, int completed, String model, Instant now) {
        return writes.whenActive(job.ownerId(), false, () -> jdbc.update("""
                UPDATE document_translation_job
                SET status = ?, total_segments = ?, completed_segments = GREATEST(completed_segments, ?),
                    model_revision = ?, updated_at = ?
                WHERE id = ? AND lease_token = ? AND lease_until >= ? AND input_key = CONCAT('ephemeral-', id)
                    AND status IN ('VALIDATING', 'TRANSLATING', 'REPACKING')
                """, stage.name(), total, completed, model, time(now), job.id().toString(),
                value(job.leaseToken()), time(now)) == 1);
    }

    // 처리 시간이 긴 외부 호출 중에도 살아 있는 워커의 리스를 유지한다.
    boolean renew(DocumentJob job, Instant now) {
        return writes.whenActive(job.ownerId(), false, () -> jdbc.update("""
                UPDATE document_translation_job SET lease_until = ?
                WHERE id = ? AND lease_token = ? AND lease_until >= ? AND input_key = CONCAT('ephemeral-', id)
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
                    AND input_key = CONCAT('ephemeral-', id)
                """, Integer.class, job.id().toString(), job.ownerId(), value(job.leaseToken()), stage.name(),
                time(now));
        return count != null && count == 1;
    }

    // 현재 임대가 만든 결과만 공개하고, 번역 완료부터 별도의 미수령 기한을 시작한다.
    boolean succeed(DocumentJob job, String key, long bytes, List<DocumentJob.Warning> warnings, Instant now) {
        if (!resultKey(job.id()).equals(key)) {
            throw new IllegalArgumentException("A result from the same transient document workspace is required");
        }
        return writes.whenActive(job.ownerId(), false, () -> jdbc.update("""
                UPDATE document_translation_job
                SET status = 'SUCCEEDED', output_key = ?, output_sha256 = NULL, output_bytes = ?, warnings = ?,
                    lease_token = NULL, lease_until = NULL, updated_at = ?, finished_at = ?, expires_at = ?
                WHERE id = ? AND owner_id = ? AND input_key = ? AND lease_token = ?
                    AND status = 'REPACKING' AND lease_until >= ? AND expires_at > ?
                """, key, bytes, json(warnings), time(now), time(now), time(now.plusSeconds(RESULT_RETENTION_SECONDS)),
                job.id().toString(), job.ownerId(), workspaceKey(job.id()), value(job.leaseToken()),
                time(now), time(now)) == 1);
    }

    // 전송을 열기 전에 활성 소유자가 같은 결과의 다운로드 권한을 한 번만 획득한다.
    boolean claimOutput(long ownerId, UUID id, String expectedOutputKey, Instant now) {
        if (!resultKey(id).equals(expectedOutputKey)) {
            return false;
        }
        return writes.whenActive(ownerId, false, () -> jdbc.update("""
                UPDATE document_translation_job SET output_key = NULL, output_sha256 = NULL, updated_at = ?
                WHERE owner_id = ? AND id = ? AND input_key = ? AND status = 'SUCCEEDED'
                    AND output_key = ? AND expires_at > ?
                """, time(now), ownerId, id.toString(), workspaceKey(id), expectedOutputKey, time(now)) == 1);
    }

    // 진행 중인 단계에서만 실패를 확정한다.
    void fail(DocumentJob job, String errorCode, Instant now) {
        writes.whenActive(job.ownerId(), 0, () -> jdbc.update("""
                UPDATE document_translation_job SET status = 'FAILED', error_code = ?, lease_token = NULL,
                    lease_until = NULL, updated_at = ?, finished_at = ?
                WHERE id = ? AND lease_token = ? AND lease_until >= ? AND input_key = CONCAT('ephemeral-', id)
                    AND status IN ('VALIDATING', 'TRANSLATING', 'REPACKING')
                """, errorCode, time(now), time(now), job.id().toString(), value(job.leaseToken()), time(now)));
    }

    // 대기 작업과 미수령 결과만 즉시 취소하고 이미 전송 권한을 획득한 결과는 유지한다.
    boolean requestCancel(long ownerId, UUID id, Instant now) {
        return writes.whenActive(ownerId, false, () -> jdbc.update("""
                UPDATE document_translation_job
                SET finished_at = CASE WHEN status = 'QUEUED' THEN ? ELSE finished_at END,
                    status = CASE WHEN status IN ('QUEUED', 'SUCCEEDED') THEN 'CANCELLED' ELSE 'CANCELLING' END,
                    output_key = NULL, output_sha256 = NULL,
                    updated_at = ?
                WHERE owner_id = ? AND id = ? AND input_key = ? AND expires_at > ?
                    AND (status IN ('QUEUED', 'VALIDATING', 'TRANSLATING', 'REPACKING')
                        OR (status = 'SUCCEEDED' AND output_key = ?))
                """, time(now), time(now), ownerId, id.toString(), workspaceKey(id), time(now), resultKey(id)) == 1);
    }

    // 현재 워커가 취소 경계를 지난 작업을 정리한다.
    void completeCancel(DocumentJob job, Instant now) {
        writes.whenActive(job.ownerId(), 0, () -> jdbc.update("""
                UPDATE document_translation_job SET status = 'CANCELLED', lease_token = NULL, lease_until = NULL,
                    updated_at = ?, finished_at = ?
                WHERE id = ? AND lease_token = ? AND status = 'CANCELLING' AND input_key = CONCAT('ephemeral-', id)
                """, time(now), time(now), job.id().toString(), value(job.leaseToken())));
    }

    // 중단한 워커를 기다리지 않고 임대 만료 취소와 전체 마감을 확정한다.
    void sweep(Instant now) {
        Instant jobLimit = now.minusSeconds(DocumentTranslationService.JOB_SECONDS);
        jdbc.update("""
                UPDATE document_translation_job SET status = 'CANCELLED', lease_token = NULL, lease_until = NULL,
                    updated_at = ?, finished_at = ?
                WHERE status = 'CANCELLING' AND lease_until < ? AND input_key = CONCAT('ephemeral-', id)
                """, time(now), time(now), time(now));
        jdbc.update("""
                UPDATE document_translation_job SET status = 'FAILED',
                    error_code = CASE WHEN created_at <= ? THEN 'TRANSLATION_TIMEOUT' ELSE 'WORKER_UNAVAILABLE' END,
                    lease_token = NULL, lease_until = NULL, updated_at = ?, finished_at = ?
                WHERE status IN ('QUEUED', 'VALIDATING', 'TRANSLATING', 'REPACKING')
                    AND input_key = CONCAT('ephemeral-', id)
                    AND (created_at <= ? OR lease_until < ?)
                """, time(jobLimit), time(now), time(now), time(jobLimit), time(now));

        // 내려받기 권한이 남은 성공 행만 만료한다. 전송 중인 결과의 물리 정리는 작업 공간이 소유한다.
        jdbc.update("""
                UPDATE document_translation_job
                SET status = 'EXPIRED', output_key = NULL, output_sha256 = NULL, updated_at = ?
                WHERE status = 'SUCCEEDED' AND expires_at <= ? AND input_key = CONCAT('ephemeral-', id)
                    AND output_key = CONCAT('ephemeral-result-', id)
                ORDER BY expires_at, id LIMIT 200
                """, time(now), time(now));
    }

    // 이전 작업 공간을 소유 잠금 안에서 비운 뒤에만, 그 파일을 가리킨 상태를 종료한다.
    void invalidateEphemeralJobs(Instant now) {
        jdbc.update("""
                UPDATE document_translation_job
                SET error_code = CASE WHEN status IN ('QUEUED', 'VALIDATING', 'TRANSLATING', 'REPACKING')
                        THEN 'WORKER_UNAVAILABLE' ELSE error_code END,
                    finished_at = COALESCE(finished_at, ?),
                    expires_at = CASE WHEN status = 'SUCCEEDED' AND output_key IS NOT NULL THEN ? ELSE expires_at END,
                    status = CASE
                        WHEN status IN ('QUEUED', 'VALIDATING', 'TRANSLATING', 'REPACKING') THEN 'FAILED'
                        WHEN status = 'CANCELLING' THEN 'CANCELLED'
                        WHEN status = 'SUCCEEDED' AND output_key IS NOT NULL THEN 'EXPIRED'
                        ELSE status END,
                    input_key = NULL, output_key = NULL, output_sha256 = NULL,
                    lease_token = NULL, lease_until = NULL, updated_at = ?
                WHERE input_key = CONCAT('ephemeral-', id)
                """, time(now), time(now), time(now));
    }

    // 이전 저장 방식의 작업을 새 처리에서 차단하고, 별도 정리에 필요한 객체 참조는 유지한다.
    void fenceLegacyJobs(Instant now) {
        jdbc.update("""
                UPDATE document_translation_job
                SET status = 'EXPIRED', expires_at = LEAST(expires_at, ?), finished_at = COALESCE(finished_at, ?),
                    lease_token = NULL, lease_until = NULL, updated_at = ?
                WHERE status <> 'EXPIRED' AND (
                    (input_key IS NOT NULL AND input_key <> CONCAT('ephemeral-', id))
                    OR (input_key IS NULL AND (input_sha256 <> ? OR output_key IS NOT NULL)))
                """, time(now), time(now), time(now), UNUSED_CONTENT_HASH);
    }

    // 호출자가 실제 파일 정리를 완료한 뒤 해당 작업 공간만 잊고 결과 통계는 유지한다.
    void forgetWorkspace(long ownerId, UUID id, Instant now) {
        jdbc.update("""
                UPDATE document_translation_job SET input_key = NULL, updated_at = ?
                WHERE owner_id = ? AND id = ? AND input_key = ? AND output_key IS NULL
                    AND status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')
                """, time(now), ownerId, id.toString(), workspaceKey(id));
    }

    // 독립 정리가 담당하는 임시 표식은 7일 뒤 제거하고, 이전 객체의 정리 증거는 보존한다.
    void removeOldMetadata(Instant now) {
        jdbc.update("""
                DELETE FROM document_translation_job
                WHERE created_at <= ? AND status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')
                    AND (input_key IS NULL OR input_key = CONCAT('ephemeral-', id)) AND output_key IS NULL
                    AND NOT EXISTS (SELECT 1 FROM document_translation_batch
                        WHERE job_id = document_translation_job.id)
                    AND NOT EXISTS (SELECT 1 FROM document_translation_object_write
                        WHERE job_id = document_translation_job.id)
                LIMIT 200
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

    // 작업과 같은 UUID를 가진 임시 디렉터리 표식만 DB 경계에 사용한다.
    private static String workspaceKey(UUID id) {
        return "ephemeral-" + id;
    }

    // 실제 경로 대신 해당 작업의 단일 결과 권한을 나타내는 표식이다.
    private static String resultKey(UUID id) {
        return "ephemeral-result-" + id;
    }
}
