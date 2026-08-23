package jp.co.translacat.domain.languagelearning.listening.response.entity;

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

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceLevel;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_listening_task_response",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ll_listening_response_attempt_task",
                        columnNames = {"attempt_id", "task_type"}
                ),
                @UniqueConstraint(
                        name = "uk_ll_listening_response_attempt_key",
                        columnNames = {"attempt_id", "idempotency_key"}
                )
        },
        indexes = @Index(
                name = "idx_ll_listening_response_retention",
                columnList = "audio_retention_until,audio_deleted_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningTaskResponse extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false)
    private ListeningItemAttempt attempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 40)
    private ListeningTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListeningTaskStatus status;

    @Lob
    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Lob
    @Column(name = "normalized_answer_text", columnDefinition = "TEXT")
    private String normalizedAnswerText;

    @Column(name = "user_audio_object_key", length = 700)
    private String userAudioObjectKey;

    @Column(name = "audio_duration_ms")
    private Integer audioDurationMs;

    @Column(name = "audio_content_type", length = 100)
    private String audioContentType;

    @Column(name = "audio_retention_until")
    private LocalDateTime audioRetentionUntil;

    @Column(name = "audio_deleted_at")
    private LocalDateTime audioDeletedAt;

    @Column(name = "rerecord_count", nullable = false)
    private int rerecordCount;

    @Column(name = "excluded_from_evaluation", nullable = false)
    private boolean excludedFromEvaluation;

    @Enumerated(EnumType.STRING)
    @Column(name = "assistance_level", nullable = false, length = 20)
    private ListeningAssistanceLevel assistanceLevel;

    @Lob
    @Column(name = "assistance_usage", nullable = false, columnDefinition = "TEXT")
    private String assistanceUsageJson;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "automatic_retry_count", nullable = false)
    private int automaticRetryCount;

    @Column(name = "manual_retry_count", nullable = false)
    private int manualRetryCount;

    @Column(name = "evaluation_error_code", length = 100)
    private String evaluationErrorCode;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Version
    private long version;

    private ListeningTaskResponse(
            ListeningItemAttempt attempt,
            ListeningTaskType taskType,
            ListeningTaskStatus status,
            String idempotencyKey
    ) {
        this.attempt = attempt;
        this.taskType = taskType;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.assistanceLevel = ListeningAssistanceLevel.INDEPENDENT;
        this.assistanceUsageJson = "[]";
    }

    public static ListeningTaskResponse selected(
            ListeningItemAttempt attempt,
            ListeningTaskType taskType,
            String idempotencyKey
    ) {
        return new ListeningTaskResponse(
                attempt,
                taskType,
                ListeningTaskStatus.READY,
                idempotencyKey
        );
    }

    public static ListeningTaskResponse notSelected(
            ListeningItemAttempt attempt,
            ListeningTaskType taskType,
            String idempotencyKey
    ) {
        return new ListeningTaskResponse(
                attempt,
                taskType,
                ListeningTaskStatus.NOT_SELECTED,
                idempotencyKey
        );
    }

    public void updateText(String answer, String normalizedAnswer) {
        requireMutable();
        this.answerText = answer;
        this.normalizedAnswerText = normalizedAnswer;
        this.status = ListeningTaskStatus.IN_PROGRESS;
    }

    public void updateAudio(
            String objectKey,
            int durationMs,
            String contentType,
            LocalDateTime retentionUntil,
            boolean rerecord
    ) {
        requireMutable();
        if (rerecord) {
            rerecordCount++;
        }
        userAudioObjectKey = objectKey;
        audioDurationMs = durationMs;
        audioContentType = contentType;
        audioRetentionUntil = retentionUntil;
        audioDeletedAt = null;
        status = ListeningTaskStatus.IN_PROGRESS;
    }

    public void applyAssistance(
            ListeningAssistanceLevel level,
            String assistanceUsageJson
    ) {
        requireMutable();
        assistanceLevel = level == null
                ? ListeningAssistanceLevel.INDEPENDENT
                : level;
        this.assistanceUsageJson = assistanceUsageJson == null
                ? "[]"
                : assistanceUsageJson;
    }

    public void excludeFromEvaluation() {
        requireMutable();
        excludedFromEvaluation = true;
    }

    public void submit(LocalDateTime now) {
        if (status == ListeningTaskStatus.NOT_SELECTED) {
            return;
        }
        requireMutable();
        status = ListeningTaskStatus.SUBMITTED;
        submittedAt = now;
    }

    public void markEvaluating() {
        if (status == ListeningTaskStatus.SUBMITTED
                || status == ListeningTaskStatus.EVALUATING) {
            status = ListeningTaskStatus.EVALUATING;
        }
    }

    public void markEvaluated() {
        status = ListeningTaskStatus.EVALUATED;
        evaluationErrorCode = null;
    }

    public void markNotEvaluable() {
        status = ListeningTaskStatus.NOT_EVALUABLE;
        evaluationErrorCode = null;
    }

    public void markEvaluationFailed(
            String errorCode,
            boolean automaticRetry,
            boolean exhausted
    ) {
        evaluationErrorCode = errorCode;
        if (automaticRetry) {
            automaticRetryCount++;
        }
        if (exhausted) {
            status = ListeningTaskStatus.EVALUATION_FAILED;
        }
    }

    public void prepareManualRetry(int limit) {
        if (status != ListeningTaskStatus.EVALUATION_FAILED
                || manualRetryCount >= limit) {
            throw new IllegalStateException(
                    "수동 평가 재시도를 실행할 수 없습니다."
            );
        }
        manualRetryCount++;
        status = ListeningTaskStatus.SUBMITTED;
        evaluationErrorCode = null;
    }

    public void skip() {
        if (status != ListeningTaskStatus.NOT_SELECTED) {
            status = ListeningTaskStatus.SKIPPED;
        }
    }

    public boolean hasRequiredAnswer() {
        if (status == ListeningTaskStatus.NOT_SELECTED) {
            return true;
        }
        if (taskType == ListeningTaskType.REPEAT_AFTER_AUDIO) {
            return userAudioObjectKey != null && audioDeletedAt == null;
        }
        return answerText != null && !answerText.isBlank();
    }

    public void extendAudioRetention(LocalDateTime until) {
        if (userAudioObjectKey != null
                && (audioRetentionUntil == null
                || audioRetentionUntil.isBefore(until))) {
            audioRetentionUntil = until;
        }
    }

    public void markAudioDeleted(LocalDateTime now) {
        userAudioObjectKey = null;
        audioDeletedAt = now;
    }

    private void requireMutable() {
        if (status == ListeningTaskStatus.SUBMITTED
                || status == ListeningTaskStatus.EVALUATING
                || status == ListeningTaskStatus.EVALUATED
                || status == ListeningTaskStatus.EVALUATION_FAILED
                || status == ListeningTaskStatus.NOT_EVALUABLE
                || status == ListeningTaskStatus.NOT_SELECTED
                || status == ListeningTaskStatus.SKIPPED) {
            throw new IllegalStateException(
                    "평가가 시작된 Listening 응답은 수정할 수 없습니다."
            );
        }
    }
}
