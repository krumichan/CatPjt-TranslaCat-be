package jp.co.translacat.domain.languagelearning.level.entity;

import jakarta.persistence.*;

import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_level_test_response",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ll_level_response_item",
                        columnNames = "item_id"
                ),
                @UniqueConstraint(
                        name = "uk_ll_level_response_key",
                        columnNames = {"item_id", "idempotency_key"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelTestResponse extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private LevelTestItem item;

    @Column(name = "selected_option_key", length = 100)
    private String selectedOptionKey;

    @Lob
    @Column(name = "selected_option_keys_json", columnDefinition = "TEXT")
    private String selectedOptionKeysJson;

    @Lob
    @Column(name = "text_answer", columnDefinition = "TEXT")
    private String textAnswer;

    @Column(name = "audio_object_key", length = 1000)
    private String audioObjectKey;

    @Column(name = "audio_content_type", length = 100)
    private String audioContentType;

    @Column(name = "audio_duration_ms")
    private Integer audioDurationMs;

    @Column(name = "audio_retention_until")
    private LocalDateTime audioRetentionUntil;

    @Column(name = "audio_deleted_at")
    private LocalDateTime audioDeletedAt;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "manual_evaluation_retry_count", nullable = false)
    private int manualEvaluationRetryCount;

    private LevelTestResponse(
            LevelTestItem item,
            String selectedOptionKey,
            String selectedOptionKeysJson,
            String textAnswer,
            String audioObjectKey,
            String audioContentType,
            Integer audioDurationMs,
            LocalDateTime audioRetentionUntil,
            String idempotencyKey,
            LocalDateTime submittedAt
    ) {
        this.item = item;
        this.selectedOptionKey = selectedOptionKey;
        this.selectedOptionKeysJson = selectedOptionKeysJson;
        this.textAnswer = textAnswer;
        this.audioObjectKey = audioObjectKey;
        this.audioContentType = audioContentType;
        this.audioDurationMs = audioDurationMs;
        this.audioRetentionUntil = audioRetentionUntil;
        this.idempotencyKey = idempotencyKey;
        this.submittedAt = submittedAt;
    }

    public static LevelTestResponse text(
            LevelTestItem item,
            String selectedOptionKey,
            String selectedOptionKeysJson,
            String textAnswer,
            String idempotencyKey,
            LocalDateTime submittedAt
    ) {
        return new LevelTestResponse(
                item,
                selectedOptionKey,
                selectedOptionKeysJson,
                textAnswer,
                null,
                null,
                null,
                null,
                idempotencyKey,
                submittedAt
        );
    }

    public static LevelTestResponse audio(
            LevelTestItem item,
            String objectKey,
            String contentType,
            int durationMs,
            LocalDateTime retentionUntil,
            String idempotencyKey,
            LocalDateTime submittedAt
    ) {
        return new LevelTestResponse(
                item,
                null,
                "[]",
                null,
                objectKey,
                contentType,
                durationMs,
                retentionUntil,
                idempotencyKey,
                submittedAt
        );
    }

    public void replaceAudio(
            String objectKey,
            String contentType,
            int durationMs,
            LocalDateTime retentionUntil,
            String idempotencyKey,
            LocalDateTime submittedAt
    ) {
        selectedOptionKey = null;
        selectedOptionKeysJson = "[]";
        textAnswer = null;
        audioObjectKey = objectKey;
        audioContentType = contentType;
        audioDurationMs = durationMs;
        audioRetentionUntil = retentionUntil;
        audioDeletedAt = null;
        this.idempotencyKey = idempotencyKey;
        this.submittedAt = submittedAt;
        manualEvaluationRetryCount = 0;
    }

    public int registerManualEvaluationRetry() {
        if (manualEvaluationRetryCount >= 1) {
            throw new IllegalStateException(
                    "Level Test 수동 평가 재시도는 1회만 가능합니다."
            );
        }
        manualEvaluationRetryCount++;
        return manualEvaluationRetryCount;
    }

    public void markAudioDeleted(LocalDateTime now) {
        audioDeletedAt = now;
        audioObjectKey = null;
    }
}
