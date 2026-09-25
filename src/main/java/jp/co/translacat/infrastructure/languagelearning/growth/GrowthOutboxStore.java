package jp.co.translacat.infrastructure.languagelearning.growth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Core JPA와 동일 DataSource의 JdbcTemplate만 사용한다.
 * 기록은 호출 트랜잭션에 참여하고, 선점/확인/재시도는 짧은 별도 트랜잭션이다.
 * SQL에 원격 DB/서비스 호출을 넣지 않는다.
 */
public class GrowthOutboxStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate isolated;
    private final Clock clock;

    public GrowthOutboxStore(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        isolated = new TransactionTemplate(manager);
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void verifySchema() {
        jdbc.queryForList(
                "SELECT source_instance_id, user_id, last_sequence FROM language_learning_growth_outbox_stream WHERE 1=0");
        jdbc.queryForList(
                "SELECT event_id, schema_version, source_instance_id, user_id, stream_sequence, occurred_at, payload_json, payload_sha256, attempts, blocked, claim_token, lease_until, next_attempt_at, delivered_at, last_error_code FROM language_learning_growth_outbox WHERE 1=0");
    }

    public GrowthEnvelope append(String sourceId, long userId, String payloadJson) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(Objects.requireNonNull(jdbc.getDataSource()))) {
            throw new IllegalStateException("결과 원장은 동일 Core DataSource가 연결된 평가 쓰기 트랜잭션 안에서만 기록합니다.");
        }
        if (!UUID.fromString(sourceId).toString().equals(sourceId))
            throw new IllegalArgumentException("source UUID를 확인해 주세요.");
        if (userId <= 0 || payloadJson == null || payloadJson.getBytes(StandardCharsets.UTF_8).length > 1_048_576)
            throw new IllegalArgumentException("성장 본문 크기/사용자를 확인해 주세요.");
        String hash = hash(payloadJson);
        // 이 행은 BEFORE_COMMIT에서 잡는다. user/core 잠금을 잡기 위해 다시 역방향으로 호출하지 않는다.
        jdbc.update(
                "INSERT INTO language_learning_growth_outbox_stream(source_instance_id,user_id,last_sequence) VALUES(?,?,0) "
                        + "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)", sourceId, userId);
        Long previous = jdbc.queryForObject("SELECT last_sequence FROM language_learning_growth_outbox_stream "
                + "WHERE source_instance_id=? AND user_id=? FOR UPDATE", Long.class, sourceId, userId);
        long sequence = Math.addExact(Objects.requireNonNull(previous), 1L);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        GrowthEnvelope event = new GrowthEnvelope(1, sourceId, UUID.randomUUID().toString(), userId, sequence,
                now.toString(), payloadJson, hash);
        jdbc.update(
                "INSERT INTO language_learning_growth_outbox(event_id,schema_version,source_instance_id,user_id,stream_sequence,"
                        + "occurred_at,payload_json,payload_sha256,attempts,blocked,next_attempt_at) "
                        + "VALUES(?,1,?,?,?,?,?,?,0,FALSE,?)",
                event.eventId(), sourceId, event.userId(), sequence, event.occurredAt(),
                event.payloadJson(), hash, Timestamp.from(now));
        jdbc.update(
                "UPDATE language_learning_growth_outbox_stream SET last_sequence=? WHERE source_instance_id=? AND user_id=?",
                sequence, sourceId, userId);
        return event;
    }

    public Optional<Claim> claim(String sourceId, int leaseSeconds, int maximumAttempts) {
        return claimForUser(sourceId, null, leaseSeconds, maximumAttempts);
    }

    public Optional<Claim> claimForUser(String sourceId, Long userId, int leaseSeconds, int maximumAttempts) {
        if (maximumAttempts < 1) throw new IllegalArgumentException("최대 시도 수는 양수여야 합니다.");
        return Objects.requireNonNull(isolated.execute(status -> {
            Instant now = databaseNow();
            // 선행 이벤트는 blocked 상태여도 뒤 이벤트를 막는다. sequence는 커밋 순서대로만 추가된다.
            List<String> candidates = jdbc.queryForList("SELECT e.event_id FROM language_learning_growth_outbox e "
                            + "WHERE e.source_instance_id=? AND (? IS NULL OR e.user_id=?) AND e.delivered_at IS NULL AND e.blocked=FALSE AND e.next_attempt_at<=? "
                            + "AND (e.claim_token IS NULL OR e.lease_until<=?) "
                            + "AND NOT EXISTS (SELECT 1 FROM language_learning_growth_outbox earlier WHERE "
                            + "earlier.source_instance_id=e.source_instance_id AND earlier.user_id=e.user_id "
                            + "AND earlier.stream_sequence<e.stream_sequence AND earlier.delivered_at IS NULL) "
                            + "ORDER BY e.next_attempt_at,e.event_id LIMIT 20", String.class, sourceId, userId, userId,
                    Timestamp.from(now),
                    Timestamp.from(now));
            for (String id : candidates) {
                // 매번 응답 전에 프로세스가 종료되어도 시도 상한을 무한히 넘기지 않는다.
                int exhausted = jdbc.update(
                        "UPDATE language_learning_growth_outbox SET blocked=TRUE,last_error_code='ATTEMPTS_EXHAUSTED',"
                                + "claim_token=NULL,lease_until=NULL WHERE event_id=? AND delivered_at IS NULL AND blocked=FALSE "
                                + "AND attempts>=? AND (claim_token IS NULL OR lease_until<=?)",
                        id, maximumAttempts, Timestamp.from(now));
                if (exhausted == 1) continue;
                String token = UUID.randomUUID().toString();
                int updated = jdbc.update(
                        "UPDATE language_learning_growth_outbox SET claim_token=?,lease_until=?,attempts=attempts+1 "
                                + "WHERE event_id=? AND delivered_at IS NULL AND blocked=FALSE AND attempts<? AND next_attempt_at<=? "
                                + "AND (claim_token IS NULL OR lease_until<=?)",
                        token, Timestamp.from(now.plusSeconds(leaseSeconds)), id, maximumAttempts, Timestamp.from(now),
                        Timestamp.from(now));
                if (updated == 1) {
                    return Optional.of(Objects.requireNonNull(jdbc.queryForObject(
                            "SELECT * FROM language_learning_growth_outbox WHERE event_id=?",
                            (rs, row) -> new Claim(envelope(rs), token, rs.getInt("attempts")), id)));
                }
            }
            return Optional.empty();
        }));
    }

    public boolean acknowledge(Claim claim, GrowthAcknowledgement ack) {
        if (ack == null || !ack.matches(claim.event()))
            throw new IllegalArgumentException("원장 ACK가 전달 이벤트와 일치하지 않습니다.");
        return Boolean.TRUE.equals(isolated.execute(status -> jdbc.update(
                "UPDATE language_learning_growth_outbox SET delivered_at=?,claim_token=NULL,lease_until=NULL,last_error_code=NULL "
                        + "WHERE event_id=? AND claim_token=? AND delivered_at IS NULL",
                Timestamp.from(databaseNow()), claim.event().eventId(), claim.token()) == 1));
    }

    public boolean fail(Claim claim, String code, boolean permanent, int maximumAttempts) {
        if (!List.of("REMOTE_UNAVAILABLE", "REMOTE_REJECTED", "ACK_MISMATCH").contains(code)) {
            throw new IllegalArgumentException("안전한 오류 코드만 저장할 수 있습니다.");
        }
        return Boolean.TRUE.equals(isolated.execute(status -> jdbc.update(
                "UPDATE language_learning_growth_outbox SET claim_token=NULL,lease_until=NULL,blocked=?,last_error_code=?,next_attempt_at=? "
                        + "WHERE event_id=? AND claim_token=? AND delivered_at IS NULL",
                permanent || claim.attempts() >= maximumAttempts, code,
                Timestamp.from(databaseNow().plusSeconds(Math.min(300L, 1L << Math.min(claim.attempts(), 8)))),
                claim.event().eventId(), claim.token()) == 1));
    }

    public long committedSequence(String sourceId, long userId) {
        return Objects.requireNonNull(isolated.execute(status -> {
            var values = jdbc.queryForList(
                    "SELECT last_sequence FROM language_learning_growth_outbox_stream WHERE source_instance_id=? AND user_id=?",
                    Long.class, sourceId, userId);
            return values.isEmpty() ? 0L : values.getFirst();
        }));
    }

    public long deliveredSequence(String sourceId, long userId) {
        return Objects.requireNonNull(isolated.execute(status -> {
            var value = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(stream_sequence),0) FROM language_learning_growth_outbox WHERE source_instance_id=? AND user_id=? AND delivered_at IS NOT NULL",
                    Long.class, sourceId, userId);
            return value == null ? 0L : value;
        }));
    }

    public void verifyOrigin(String sourceId) {
        var origins = jdbc.queryForList(
                "SELECT DISTINCT source_instance_id FROM language_learning_growth_outbox_stream LIMIT 2", String.class);
        if (origins.stream().anyMatch(value -> !value.equals(sourceId)))
            throw new IllegalStateException("Core 성장 outbox의 source UUID를 변경할 수 없습니다.");
    }

    public static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private Instant databaseNow() {
        // 다중 BE 인스턴스의 시계 차이로 lease를 잘못 만료시키지 않도록 Core DB 시각을 사용한다.
        return Objects.requireNonNull(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)", Timestamp.class)).toInstant();
    }

    private GrowthEnvelope envelope(ResultSet rs) throws SQLException {
        return new GrowthEnvelope(rs.getInt("schema_version"), rs.getString("source_instance_id"),
                rs.getString("event_id"), rs.getLong("user_id"),
                rs.getLong("stream_sequence"),
                rs.getString("occurred_at"),
                rs.getString("payload_json"), rs.getString("payload_sha256"));
    }

    public record Claim(GrowthEnvelope event, String token, int attempts) {
        @Override
        public String toString() {
            return "GrowthClaim(eventId=" + event.eventId() + ", attempts=" + attempts + ")";
        }
    }
}
