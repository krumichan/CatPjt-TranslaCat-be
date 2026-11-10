package jp.co.translacat.domain.accountbook.analysis.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.Request;
import jp.co.translacat.domain.accountbook.analysis.service.SpendingAnalysisPeriods.Range;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SpendingAnalysisJobStore {
    public enum Status {
        QUEUED, RUNNING, CANCELING, SUCCEEDED, FAILED, CANCELED
    }

    public record Job(long id, long bookId, long requestedBy, LocalDate start, LocalDate endExclusive,
                      Status status, String analysisId, String errorCode) { }

    private static final RowMapper<Job> JOB = (row, index) -> new Job(
            row.getLong("id"), row.getLong("account_book_id"), row.getLong("requested_by"),
            row.getObject("period_start", LocalDate.class), row.getObject("period_end_exclusive", LocalDate.class),
            Status.valueOf(row.getString("status")), row.getString("analysis_id"), row.getString("error_code"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public Job insert(long bookId, long userId, Request request, Range range, Instant leaseUntil) {
        // DB 유일 키가 서로 다른 서버와 기존 동기 호출의 중복 접수도 막는다.
        String json;
        try {
            json = mapper.writeValueAsString(request);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode spending analysis request");
        }

        var key = new GeneratedKeyHolder();
        var status = leaseUntil == null ? Status.QUEUED : Status.RUNNING;
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO account_book_spending_analysis_job
                        (account_book_id, requested_by, period_start, period_end_exclusive, status, request_json, lease_until)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, bookId);
            statement.setLong(2, userId);
            statement.setObject(3, range.start());
            statement.setObject(4, range.end());
            statement.setString(5, status.name());
            statement.setString(6, json);
            statement.setTimestamp(7, leaseUntil == null ? null : Timestamp.from(leaseUntil));
            return statement;
        }, key);
        return new Job(key.getKey().longValue(), bookId, userId, range.start(), range.end(), status, null, null);
    }

    public Optional<Job> active(long bookId) {
        return jdbc.query("SELECT * FROM account_book_spending_analysis_job WHERE active_book_id=?", JOB, bookId)
                .stream().findFirst();
    }

    public Optional<Job> latestRequested(long bookId, long userId) {
        return jdbc.query("""
                SELECT * FROM account_book_spending_analysis_job
                WHERE account_book_id=? AND requested_by=? ORDER BY id DESC LIMIT 1
                """, JOB, bookId, userId).stream().findFirst();
    }

    public Optional<Job> find(long bookId, long jobId) {
        return jdbc.query("SELECT * FROM account_book_spending_analysis_job WHERE account_book_id=? AND id=?", JOB, bookId, jobId)
                .stream().findFirst();
    }

    public Optional<Job> lock(long bookId, long jobId) {
        return jdbc.query("""
                SELECT * FROM account_book_spending_analysis_job WHERE account_book_id=? AND id=? FOR UPDATE
                """, JOB, bookId, jobId).stream().findFirst();
    }

    public Optional<Job> nextQueued() {
        return jdbc.query("SELECT * FROM account_book_spending_analysis_job WHERE status='QUEUED' ORDER BY id LIMIT 1", JOB)
                .stream().findFirst();
    }

    public boolean claim(long id, Instant leaseUntil) {
        return jdbc.update("""
                UPDATE account_book_spending_analysis_job SET status='RUNNING', lease_until=?
                WHERE id=? AND status='QUEUED'
                """, Timestamp.from(leaseUntil), id) == 1;
    }

    public Request request(long id) {
        // 대기 중 취소된 작업은 본문을 이미 지웠으므로 실행하지 않는다.
        String json = jdbc.query("""
                SELECT request_json FROM account_book_spending_analysis_job WHERE id=? AND status='RUNNING'
                """, (row, index) -> row.getString(1), id).stream().findFirst().orElse(null);
        if (json == null) return null;
        try {
            return mapper.readValue(json, Request.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not decode spending analysis request");
        }
    }

    public boolean canCallProvider(long id, Instant minimumLease) {
        return !jdbc.queryForList("""
                SELECT id FROM account_book_spending_analysis_job WHERE id=? AND status='RUNNING' AND lease_until>?
                """, Long.class, id, Timestamp.from(minimumLease)).isEmpty();
    }

    public void cancel(long id) {
        // 실행 중이면 기존 외부 호출이 정리될 때까지 같은 가계부의 슬롯을 유지한다.
        jdbc.update("""
                UPDATE account_book_spending_analysis_job
                SET status=CASE WHEN status='QUEUED' THEN 'CANCELED' ELSE 'CANCELING' END, request_json=NULL
                WHERE id=? AND status IN ('QUEUED', 'RUNNING')
                """, id);
    }

    public boolean complete(long id, String analysisId, Instant now) {
        return jdbc.update("""
                UPDATE account_book_spending_analysis_job SET status='SUCCEEDED', analysis_id=?, request_json=NULL, lease_until=NULL
                WHERE id=? AND status='RUNNING' AND lease_until>?
                """, analysisId, id, Timestamp.from(now)) == 1;
    }

    public void finishWithoutResult(long id, String error) {
        jdbc.update("""
                UPDATE account_book_spending_analysis_job
                SET error_code=CASE WHEN status='RUNNING' THEN ? ELSE NULL END,
                    status=CASE WHEN status='CANCELING' THEN 'CANCELED' ELSE 'FAILED' END,
                    request_json=NULL, lease_until=NULL
                WHERE id=? AND status IN ('RUNNING', 'CANCELING')
                """, error, id);
    }

    public void expire(Instant now) {
        // 재기동 전의 실행은 재호출하지 않고 실패로 닫아 중복 비용을 방지한다.
        jdbc.update("""
                UPDATE account_book_spending_analysis_job
                SET error_code=CASE WHEN status='RUNNING' THEN 'SPENDING_ANALYSIS_UNAVAILABLE' ELSE NULL END,
                    status=CASE WHEN status='CANCELING' THEN 'CANCELED' ELSE 'FAILED' END,
                    request_json=NULL, lease_until=NULL
                WHERE status IN ('RUNNING', 'CANCELING') AND lease_until<=?
                """, Timestamp.from(now));
    }
}
