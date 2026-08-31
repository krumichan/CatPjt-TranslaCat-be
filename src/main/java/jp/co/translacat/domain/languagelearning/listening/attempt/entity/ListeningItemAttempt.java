package jp.co.translacat.domain.languagelearning.listening.attempt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAttemptStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvaluationPurpose;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_listening_item_attempt",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ll_listening_attempt_session_item_no",
                        columnNames = {"session_id", "item_id", "attempt_no"}
                ),
                @UniqueConstraint(
                        name = "uk_ll_listening_attempt_session_key",
                        columnNames = {"session_id", "idempotency_key"}
                ),
                @UniqueConstraint(
                        name = "uk_ll_listening_attempt_item_purpose",
                        columnNames = {"item_id", "evaluation_purpose"}
                )
        },
        indexes = @Index(
                name = "idx_ll_listening_attempt_session_status",
                columnList = "session_id,status"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningItemAttempt extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private ListeningSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private ListeningItem item;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListeningAttemptStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_purpose", nullable = false, length = 20)
    private ListeningEvaluationPurpose evaluationPurpose;

    @Column(nullable = false)
    private boolean official;

    @Column(nullable = false)
    private boolean practice;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "content_overall_score")
    private Double contentOverallScore;

    @Column(name = "listening_independence_score")
    private Double listeningIndependenceScore;

    @Column(name = "normal_playback_count", nullable = false)
    private int normalPlaybackCount;

    @Column(name = "slow_playback_count", nullable = false)
    private int slowPlaybackCount;

    @Column(name = "independence_policy_version", length = 100)
    private String independencePolicyVersion;

    @Column(name = "evaluated_task_count", nullable = false)
    private int evaluatedTaskCount;

    @Column(nullable = false)
    private double coverage;

    @Lob
    @Column(name = "assistance_usage", nullable = false, columnDefinition = "TEXT")
    private String assistanceUsageJson;

    @Column(name = "answer_revealed", nullable = false)
    private boolean answerRevealed;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "evaluation_version", length = 100)
    private String evaluationVersion;

    @Column(name = "profile_applied", nullable = false)
    private boolean profileApplied;

    @Column(name = "progress_applied", nullable = false)
    private boolean progressApplied;

    @Column(name = "manual_evaluation_retry_count", nullable = false)
    private int manualEvaluationRetryCount;

    @Column(name = "actual_duration_ms", nullable = false)
    private long actualDurationMs;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Version
    private long version;

    private ListeningItemAttempt(
            ListeningSession session,
            ListeningItem item,
            int attemptNo,
            ListeningEvaluationPurpose purpose,
            String idempotencyKey,
            LocalDateTime now
    ) {
        this.session = session;
        this.item = item;
        this.attemptNo = attemptNo;
        this.evaluationPurpose = purpose;
        this.official = purpose == ListeningEvaluationPurpose.OFFICIAL;
        this.practice = purpose == ListeningEvaluationPurpose.PRACTICE;
        this.idempotencyKey = idempotencyKey;
        this.status = ListeningAttemptStatus.IN_PROGRESS;
        this.startedAt = now;
        this.assistanceUsageJson = "[]";
    }

    public static ListeningItemAttempt create(
            ListeningSession session,
            ListeningItem item,
            int attemptNo,
            ListeningEvaluationPurpose purpose,
            String idempotencyKey,
            LocalDateTime now
    ) {
        return new ListeningItemAttempt(
                session,
                item,
                attemptNo,
                purpose,
                idempotencyKey,
                now
        );
    }

    public void revealAnswer() {
        requireMutable();
        answerRevealed = true;
    }

    public void submit(
            String assistanceUsageJson,
            long actualDurationMs,
            LocalDateTime now
    ) {
        requireMutable();
        this.assistanceUsageJson = assistanceUsageJson == null
                ? "[]"
                : assistanceUsageJson;
        status = ListeningAttemptStatus.SUBMITTED;
        submittedAt = now;
        this.actualDurationMs = Math.max(0, actualDurationMs);
        errorCode = null;
    }

    public void markEvaluating() {
        if (status != ListeningAttemptStatus.SUBMITTED
                && status != ListeningAttemptStatus.EVALUATING) {
            throw new IllegalStateException(
                    "평가 가능한 Listening Attempt 상태가 아닙니다."
            );
        }
        status = ListeningAttemptStatus.EVALUATING;
    }

    public void finish(
            Double overallScore,
            int evaluatedTaskCount,
            int selectedTaskCount,
            String evaluationVersion,
            boolean profileApplied,
            LocalDateTime now
    ) {
        this.overallScore = overallScore;
        this.evaluatedTaskCount = evaluatedTaskCount;
        this.coverage = selectedTaskCount <= 0
                ? 0
                : Math.min(1.0, evaluatedTaskCount / (double) selectedTaskCount);
        this.evaluationVersion = evaluationVersion;
        this.profileApplied = official && profileApplied;
        this.status = evaluatedTaskCount == 0
                ? ListeningAttemptStatus.NOT_EVALUABLE
                : ListeningAttemptStatus.EVALUATED;
        this.evaluatedAt = now;
        this.errorCode = null;
    }

    public void applyListeningIndependence(
            Double contentOverallScore,
            Double listeningIndependenceScore,
            Double adjustedOverallScore,
            int normalPlaybackCount,
            int slowPlaybackCount,
            String policyVersion
    ) {
        this.contentOverallScore = contentOverallScore;
        this.listeningIndependenceScore = listeningIndependenceScore;
        this.overallScore = adjustedOverallScore;
        this.normalPlaybackCount = Math.max(0, normalPlaybackCount);
        this.slowPlaybackCount = Math.max(0, slowPlaybackCount);
        this.independencePolicyVersion = policyVersion;
    }

    public void skip(LocalDateTime now) {
        requireMutable();
        status = ListeningAttemptStatus.SKIPPED;
        submittedAt = now;
        evaluatedAt = now;
        overallScore = null;
        evaluatedTaskCount = 0;
        coverage = 0;
        profileApplied = false;
    }

    public void markEvaluationError(String errorCode) {
        this.errorCode = errorCode;
    }

    public void registerManualEvaluationRetry(int limit) {
        if (manualEvaluationRetryCount >= limit * 3) {
            throw new IllegalStateException(
                    "수동 평가 재시도 가능 횟수를 초과했습니다."
            );
        }
        if (progressApplied) {
            throw new IllegalStateException(
                    "이미 학습 진행도에 반영된 평가를 다시 시도할 수 없습니다."
            );
        }
        manualEvaluationRetryCount++;
        status = ListeningAttemptStatus.SUBMITTED;
        evaluatedAt = null;
        errorCode = null;
    }

    public void markProgressApplied() {
        progressApplied = true;
    }

    public boolean isFinalized() {
        return status == ListeningAttemptStatus.EVALUATED
                || status == ListeningAttemptStatus.NOT_EVALUABLE
                || status == ListeningAttemptStatus.SKIPPED;
    }

    private void requireMutable() {
        if (status != ListeningAttemptStatus.IN_PROGRESS
                && status != ListeningAttemptStatus.READY) {
            throw new IllegalStateException(
                    "수정 가능한 Listening Attempt 상태가 아닙니다."
            );
        }
    }
}
