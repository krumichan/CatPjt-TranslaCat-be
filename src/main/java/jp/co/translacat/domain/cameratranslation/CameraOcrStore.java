package jp.co.translacat.domain.cameratranslation;

import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.JobStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 카메라 OCR 작업의 상태와 단기 암호문을 단일 테이블에 저장한다. */
@Repository
class CameraOcrStore {
    private static final RowMapper<Header> HEADER = (row, index) -> new Header(
            UUID.fromString(row.getString("job_id")), row.getLong("owner_id"), row.getString("request_hash"),
            JobStatus.valueOf(row.getString("state")), row.getTimestamp("expires_at").toInstant(),
            row.getBytes("result_ciphertext"), row.getString("error_code"));
    private static final String HEADER_COLUMNS =
            "job_id, owner_id, request_hash, state, expires_at, result_ciphertext, error_code";

    private final JdbcTemplate jdbc;

    /** 공용 DataSource에서 카메라 전용 SQL만 실행한다. */
    CameraOcrStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 같은 계정의 유효한 멱등 키에 연결된 작업을 조회한다. */
    Optional<Header> byKey(long owner, UUID key, Instant now) {
        return jdbc.query("select " + HEADER_COLUMNS + """
                 from camera_ocr_job where owner_id=? and idem_key=? and expires_at>?
                """, HEADER, owner, key.toString(), time(now)).stream().findFirst();
    }

    /** 다른 계정과 만료 작업을 결과에서 제외한다. */
    Optional<Header> byId(long owner, UUID jobId, Instant now) {
        return jdbc.query("select " + HEADER_COLUMNS + """
                 from camera_ocr_job where owner_id=? and job_id=? and expires_at>?
                """, HEADER, owner, jobId.toString(), time(now)).stream().findFirst();
    }

    /** 접수 경쟁을 직렬화한 호출자가 시간당이 아닌 짧은 구간의 한도를 확인한다. */
    boolean canEnqueue(long owner, Instant now) {
        Integer recent = jdbc.queryForObject("select count(*) from camera_ocr_job where owner_id=? and created_at>?",
                Integer.class, owner, time(now.minusSeconds(60)));
        Integer active = jdbc.queryForObject("""
                select count(*) from camera_ocr_job where owner_id=? and state in ('QUEUED','RUNNING') and deadline_at>?
                """, Integer.class, owner, time(now));
        return recent != null && recent < 10 && active != null && active < 2;
    }

    /** 기존 행의 수명이 끝난 키만 다시 사용할 수 있게 한다. */
    void deleteExpiredKey(long owner, UUID key, Instant now) {
        jdbc.update("delete from camera_ocr_job where owner_id=? and idem_key=? and expires_at<=?",
                owner, key.toString(), time(now));
    }

    /** 암호화한 입력과 작업 상태를 함께 접수한다. */
    void insert(long owner, UUID jobId, UUID key, String hash, UUID imageId, String language,
            String mediaType, byte[] image, Instant now, Instant deadline, Instant expires) {
        jdbc.update("""
                insert into camera_ocr_job
                    (job_id, owner_id, idem_key, request_hash, image_id, source_lang, image_type,
                     image_ciphertext, state, created_at, deadline_at, expires_at)
                values (?,?,?,?,?,?,?,?,'QUEUED',?,?,?)
                """, jobId.toString(), owner, key.toString(), hash, imageId.toString(), language,
                mediaType, image, time(now), time(deadline), time(expires));
    }

    /** 입력과 결과를 지우고 기존 만료 시각의 취소 표식을 남긴다. */
    boolean cancel(long owner, UUID jobId, Instant now) {
        return jdbc.update("""
                update camera_ocr_job
                set state='CANCELED', image_ciphertext=null, result_ciphertext=null, error_code=null,
                    execution_token=null, lease_until=null
                where owner_id=? and job_id=? and expires_at>?
                """, owner, jobId.toString(), time(now)) > 0;
    }

    /** 임대가 가능한 소량의 후보만 찾는다. 입력 암호문은 아직 읽지 않는다. */
    List<Candidate> candidates(Instant now) {
        return jdbc.query("""
                select job_id, owner_id from camera_ocr_job
                where deadline_at>? and attempt<2 and (state='QUEUED' or (state='RUNNING' and lease_until<=?))
                order by created_at, job_id limit 8
                """, (row, index) -> new Candidate(UUID.fromString(row.getString("job_id")), row.getLong("owner_id")),
                time(now), time(now));
    }

    /** 후보의 상태가 여전히 유효할 때에만 임대와 입력을 반환한다. */
    Optional<Claim> claim(Candidate candidate, UUID token, Instant now) {
        Instant lease = now.plusSeconds(9);
        if (jdbc.update("""
                update camera_ocr_job
                set state='RUNNING', execution_token=?, lease_until=?, attempt=attempt+1
                where job_id=? and owner_id=? and deadline_at>? and attempt<2
                    and (state='QUEUED' or (state='RUNNING' and lease_until<=?))
                """, token.toString(), time(lease), candidate.id().toString(), candidate.owner(), time(now), time(now))
                != 1) {
            return Optional.empty();
        }

        // 같은 트랜잭션에서 인수한 실행의 입력만 읽는다.
        return jdbc.query("""
                select image_id, source_lang, image_type, image_ciphertext, deadline_at, attempt
                from camera_ocr_job where job_id=? and execution_token=?
                """, (row, index) -> new Claim(candidate, token, UUID.fromString(row.getString("image_id")),
                row.getString("source_lang"), row.getString("image_type"), row.getBytes("image_ciphertext"),
                row.getTimestamp("deadline_at").toInstant(), row.getInt("attempt")),
                candidate.id().toString(), token.toString()).stream().findFirst();
    }

    /** 현재 임대 토큰이 맞는 결과만 공개하거나 새 시도로 되돌린다. */
    void finish(Claim claim, JobStatus status, byte[] result, String error, Instant now) {
        jdbc.update("""
                update camera_ocr_job
                set state=?, result_ciphertext=?, error_code=?, execution_token=null, lease_until=null,
                    image_ciphertext=case when ?='QUEUED' then image_ciphertext else null end
                where job_id=? and state='RUNNING' and execution_token=? and deadline_at>? and lease_until>?
                """, status.name(), result, error, status.name(), claim.candidate().id().toString(),
                claim.token().toString(), time(now), time(now));
    }

    /** 실행 기한과 조회 기한을 정리하고 아직 설치되지 않은 스키마는 건너뛴다. */
    boolean expireIfProvisioned(Instant now) {
        try {
            jdbc.update("""
                    update camera_ocr_job set state='FAILED', error_code='OCR_DEADLINE_EXCEEDED',
                        image_ciphertext=null, execution_token=null, lease_until=null
                    where state in ('QUEUED','RUNNING') and (deadline_at<=?
                        or (state='RUNNING' and attempt>=2 and lease_until<=?))
                    """, time(now), time(now));
            jdbc.update("delete from camera_ocr_job where expires_at<=?", time(now));
            return true;
        } catch (BadSqlGrammarException failure) {
            if (isMissingTable(failure)) {
                return false;
            }
            throw failure;
        }
    }

    /** 작업과 멱등 키를 계정 단위로 제거하며 반복 호출을 허용한다. */
    void deleteOwner(long owner) {
        jdbc.update("delete from camera_ocr_job where owner_id=?", owner);
    }

    /** 미설치와 설치 상태를 구분하고, 불완전한 스키마나 DB 실패는 그대로 보고한다. */
    boolean provisioned() {
        try {
            jdbc.queryForList("""
                    select job_id, owner_id, idem_key, request_hash, image_id, source_lang, image_type,
                        image_ciphertext, result_ciphertext, state, error_code, attempt, execution_token,
                        lease_until, created_at, deadline_at, expires_at
                    from camera_ocr_job where 1=0
                    """);
            return true;
        } catch (BadSqlGrammarException failure) {
            if (isMissingTable(failure)) {
                return false;
            }
            throw failure;
        }
    }

    // 테이블 미생성만 초기 상태로 취급한다. 컬럼·권한·연결 오류는 정리를 막는다.
    private static boolean isMissingTable(BadSqlGrammarException failure) {
        return failure.getSQLException() != null && "42S02".equals(failure.getSQLException().getSQLState());
    }

    // JDBC 비교에 UTC Instant와 동일한 순간을 넘긴다.
    private static Timestamp time(Instant instant) {
        return Timestamp.from(instant);
    }

    record Header(UUID id, long owner, String requestHash, JobStatus status, Instant expiresAt,
            byte[] result, String error) {
    }

    record Candidate(UUID id, long owner) {
    }

    record Claim(Candidate candidate, UUID token, UUID imageId, String language, String contentType,
            byte[] input, Instant deadline, int attempt) {
    }
}
