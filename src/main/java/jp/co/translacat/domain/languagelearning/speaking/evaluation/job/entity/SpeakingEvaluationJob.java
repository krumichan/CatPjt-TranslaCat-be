package jp.co.translacat.domain.languagelearning.speaking.evaluation.job.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** A durable evaluation intent. Problem 0 is the session aggregate; 1..5 are Read Aloud. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "language_learning_speaking_evaluation_job",
        uniqueConstraints = @UniqueConstraint(name = "uk_ll_speaking_job_session_problem",
                columnNames = {"session_id", "problem_index"}),
        indexes = @Index(name = "idx_ll_speaking_job_due", columnList = "status,available_at,id"))
public class SpeakingEvaluationJob extends BaseAuditable {
    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private SpeakingSession session;
    @Column(name = "problem_index", nullable = false, updatable = false)
    private int problemIndex;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Lob @Column(name = "request_json", nullable = false, columnDefinition = "LONGTEXT")
    private String requestJson;
    @Column(name = "claim_token", length = 36)
    private String claimToken;
    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;
    @Column(name = "manual_retry_count", nullable = false)
    private int manualRetryCount;
    @Column(name = "recovery_count", nullable = false)
    private int recoveryCount;
    @Column(name = "last_error", length = 100)
    private String lastError;

    public static SpeakingEvaluationJob pending(SpeakingSession session, int problemIndex,
                                                 String requestJson, LocalDateTime now) {
        if (problemIndex < 0 || problemIndex > 5) throw new IllegalArgumentException("Invalid problem index");
        SpeakingEvaluationJob job = new SpeakingEvaluationJob();
        job.session = Objects.requireNonNull(session);
        job.problemIndex = problemIndex;
        job.requestJson = Objects.requireNonNull(requestJson);
        job.status = Status.PENDING;
        job.availableAt = Objects.requireNonNull(now);
        return job;
    }

    public boolean isDue(LocalDateTime now) {
        return (status == Status.PENDING || status == Status.RUNNING) && !availableAt.isAfter(now);
    }

    /** Must be called under the session -> job database locks. A stale worker loses its token. */
    public String claim(LocalDateTime now, Duration lease, int maxRecoveries) {
        if (!isDue(now)) return null;
        if (lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("Positive lease required");
        if (status == Status.RUNNING) {
            if (recoveryCount >= maxRecoveries) {
                status = Status.FAILED;
                claimToken = null;
                lastError = "EVALUATION_RECOVERY_EXHAUSTED";
                return null;
            }
            recoveryCount++;
        }
        status = Status.RUNNING;
        claimToken = UUID.randomUUID().toString();
        availableAt = now.plus(lease);
        lastError = null;
        return claimToken;
    }

    public boolean owns(String token) {
        return status == Status.RUNNING && token != null && token.equals(claimToken);
    }

    public boolean succeed(String token) {
        if (!owns(token)) return false;
        status = Status.SUCCEEDED;
        claimToken = null;
        lastError = null;
        return true;
    }

    public boolean fail(String token, String errorCode) {
        if (!owns(token)) return false;
        status = Status.FAILED;
        claimToken = null;
        // Store only our bounded error codes, not provider responses, credentials or learner text.
        lastError = errorCode;
        return true;
    }

    public boolean release(String token, LocalDateTime retryAt) {
        if (!owns(token)) return false;
        status = Status.PENDING;
        claimToken = null;
        availableAt = retryAt;
        return true;
    }

    public void retry(int limit, String requestJson, LocalDateTime now) {
        if (status != Status.FAILED || manualRetryCount >= limit)
            throw new IllegalStateException("Evaluation retry is unavailable");
        manualRetryCount++;
        recoveryCount = 0;
        this.requestJson = Objects.requireNonNull(requestJson);
        status = Status.PENDING;
        claimToken = null;
        lastError = null;
        availableAt = now;
    }
}
