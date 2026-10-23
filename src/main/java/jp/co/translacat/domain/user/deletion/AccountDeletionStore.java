package jp.co.translacat.domain.user.deletion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 삭제 전용 메타데이터만 저장한다. proof/receipt 원문은 저장하지 않는다. */
@Repository
public class AccountDeletionStore {
    private final JdbcTemplate jdbc;

    public AccountDeletionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void proof(String token, long userId, String sid, String kind, String nonce, Instant now) {
        jdbc.update("insert into account_deletion_proof (token_hash,user_id,session_id,kind,nonce_hash,created_at,expires_at) values (?,?,?,?,?,?,?)",
                DeletionTokens.hash(token), userId, sid, kind, nonce == null ? null : DeletionTokens.hash(nonce), ts(now), ts(now.plusSeconds(300)));
    }

    public Optional<Proof> proof(String token, boolean lock) {
        return jdbc.query("select * from account_deletion_proof where token_hash=?" + (lock ? " for update" : ""),
                (rs, row) -> new Proof(rs.getString("token_hash"), rs.getLong("user_id"), rs.getString("session_id"), rs.getString("kind"),
                        rs.getString("nonce_hash"), rs.getTimestamp("expires_at").toInstant(), instant(rs.getTimestamp("consumed_at"))),
                DeletionTokens.hash(DeletionTokens.secret(token))).stream().findFirst();
    }

    public void consume(Proof proof, Instant now) {
        if (jdbc.update("update account_deletion_proof set consumed_at=? where token_hash=? and consumed_at is null", ts(now), proof.hash()) != 1) throw DeletionTokens.invalid();
    }

    public Optional<Operation> operation(String id, boolean lock) {
        return jdbc.query("select * from account_deletion_operation where operation_id=?" + (lock ? " for update" : ""),
                (rs, row) -> new Operation(rs.getString("operation_id"), rs.getLong("user_id"), rs.getString("receipt_hash"), rs.getString("status"),
                        rs.getTimestamp("requested_at").toInstant(), instant(rs.getTimestamp("completed_at")), instant(rs.getTimestamp("receipt_expires_at")), rs.getString("audit_identity")), id).stream().findFirst();
    }

    public boolean existsForUser(long userId) {
        return jdbc.queryForObject("select count(*) from account_deletion_operation where user_id=?", Long.class, userId) > 0;
    }

    public void create(String id, long userId, String receiptSecret, String auditIdentity, Instant now) {
        jdbc.update("insert into account_deletion_operation (operation_id,user_id,receipt_hash,status,requested_at,audit_identity) values (?,?,?,'PENDING',?,?)",
                id, userId, DeletionTokens.hash(receiptSecret), ts(now), auditIdentity);
        for (String step : List.of("CHAT", "LL", "BE_DATA", "BE_OBJECTS")) {
            jdbc.update("insert into account_deletion_step (operation_id,step,status,attempts,next_attempt_at) values (?,?,'PENDING',0,?)", id, step, ts(now));
        }
    }

    public List<String> pending(Instant now) {
        return jdbc.queryForList("select distinct operation_id from account_deletion_step where status <> 'COMPLETED' and next_attempt_at<=? order by operation_id limit 10", String.class, ts(now));
    }

    public List<String> remaining(String id) {
        return jdbc.queryForList("select step from account_deletion_step where operation_id=? and status<>'COMPLETED' order by step", String.class, id);
    }

    public boolean claim(String id, String step, String lease, Instant now) {
        // 트랜잭션 밖의 외부 호출은 짧은 임대가 소유한다. 다른 worker의 늦은 결과는 lease 비교로 폐기한다.
        return jdbc.update("update account_deletion_step set lease_id=?,status='RUNNING',attempts=attempts+1,next_attempt_at=? where operation_id=? and step=? and status<>'COMPLETED' and next_attempt_at<=?",
                lease, ts(now.plusSeconds(90)), id, step, ts(now)) == 1;
    }

    public void finish(String id, String step, String lease, boolean complete, Instant now) {
        Integer attempts = jdbc.queryForObject("select attempts from account_deletion_step where operation_id=? and step=?", Integer.class, id, step);
        long delay = complete ? 0 : Math.min(3600, 60L << Math.min(6, Math.max(0, attempts - 1)));
        jdbc.update("update account_deletion_step set status=?,lease_id=null,next_attempt_at=? where operation_id=? and step=? and lease_id=?",
                complete ? "COMPLETED" : "PENDING", ts(now.plusSeconds(delay)), id, step, lease);
    }

    public void complete(String id, Instant now) {
        // 모든 필수 단계가 끝난 후에만 최소 상태 기록으로 전환하고 작업용 이메일을 제거한다.
        jdbc.update("update account_deletion_operation set status='COMPLETED',completed_at=?,receipt_expires_at=?,audit_identity=null where operation_id=? and status='PENDING' and not exists (select 1 from account_deletion_step where operation_id=? and status<>'COMPLETED')",
                ts(now), ts(now.plusSeconds(7 * 86400)), id, id);
    }

    public void expireReceipts(Instant now) {
        jdbc.update("update account_deletion_operation set receipt_hash=null where receipt_expires_at<=? and receipt_hash is not null", ts(now));
        jdbc.update("delete from account_deletion_proof where expires_at<?", ts(now.minusSeconds(86400)));
    }

    static Timestamp ts(Instant instant) { return Timestamp.from(instant); }
    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }

    public record Proof(String hash, long userId, String sid, String kind, String nonceHash, Instant expiresAt, Instant consumedAt) { }
    public record Operation(String operationId, long userId, String receiptHash, String status, Instant requestedAtUtc,
                            Instant completedAtUtc, Instant receiptExpiresAtUtc, String auditIdentity) { }
}
