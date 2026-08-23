package jp.co.translacat.domain.languagelearning.listening.daily.entity;

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

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_listening_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_listening_item_set_index_replacement",
                columnNames = {
                        "daily_set_id",
                        "item_index",
                        "replacement_sequence"
                }
        ),
        indexes = {
                @Index(
                        name = "idx_ll_listening_item_set_status",
                        columnList = "daily_set_id,status"
                ),
                @Index(
                        name = "idx_ll_listening_item_hash",
                        columnList = "content_hash"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningItem extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_set_id", nullable = false, updatable = false)
    private ListeningDailySet dailySet;

    @Column(name = "item_index", nullable = false)
    private int itemIndex;

    @Column(name = "replacement_sequence", nullable = false)
    private int replacementSequence;

    @Lob
    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;

    @Lob
    @Column(name = "normalized_source_text", nullable = false, columnDefinition = "TEXT")
    private String normalizedSourceText;

    @Lob
    @Column(name = "reference_meanings", nullable = false, columnDefinition = "TEXT")
    private String referenceMeaningsJson;

    @Lob
    @Column(name = "key_meaning_units", nullable = false, columnDefinition = "TEXT")
    private String keyMeaningUnitsJson;

    @Lob
    @Column(name = "target_keywords", nullable = false, columnDefinition = "TEXT")
    private String targetKeywordsJson;

    @Column(name = "estimated_audio_seconds", nullable = false)
    private double estimatedAudioSeconds;

    @Column(name = "audio_object_key", length = 700)
    private String audioObjectKey;

    @Column(name = "audio_duration_ms")
    private Integer audioDurationMs;

    @Column(name = "audio_content_type", length = 100)
    private String audioContentType;

    @Column(name = "audio_checksum", length = 128)
    private String audioChecksum;

    @Column(name = "audio_retention_until")
    private LocalDateTime audioRetentionUntil;

    @Column(name = "audio_deleted_at")
    private LocalDateTime audioDeletedAt;

    @Lob
    @Column(name = "voice_snapshot", columnDefinition = "TEXT")
    private String voiceSnapshotJson;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "similarity_key", nullable = false, length = 500)
    private String similarityKey;

    @Lob
    @Column(name = "generation_metadata", nullable = false, columnDefinition = "TEXT")
    private String generationMetadataJson;

    @Column(name = "replacement_for_item_id")
    private Long replacementForItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListeningItemStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "automatic_tts_retry_count", nullable = false)
    private int automaticTtsRetryCount;

    @Column(name = "manual_tts_retry_count", nullable = false)
    private int manualTtsRetryCount;

    @Version
    private long version;

    private ListeningItem(
            ListeningDailySet dailySet,
            int itemIndex,
            String sourceText,
            String normalizedSourceText,
            String referenceMeaningsJson,
            String keyMeaningUnitsJson,
            String targetKeywordsJson,
            double estimatedAudioSeconds,
            String contentHash,
            String similarityKey,
            String generationMetadataJson,
            Long replacementForItemId,
            int replacementSequence
    ) {
        this.dailySet = dailySet;
        this.itemIndex = itemIndex;
        this.sourceText = sourceText;
        this.normalizedSourceText = normalizedSourceText;
        this.referenceMeaningsJson = json(referenceMeaningsJson, "[]");
        this.keyMeaningUnitsJson = json(keyMeaningUnitsJson, "[]");
        this.targetKeywordsJson = json(targetKeywordsJson, "[]");
        this.estimatedAudioSeconds = estimatedAudioSeconds;
        this.contentHash = contentHash;
        this.similarityKey = similarityKey;
        this.generationMetadataJson = json(generationMetadataJson, "{}");
        this.replacementForItemId = replacementForItemId;
        this.replacementSequence = replacementSequence;
        this.status = ListeningItemStatus.TTS_PENDING;
    }

    public static ListeningItem create(
            ListeningDailySet dailySet,
            int itemIndex,
            String sourceText,
            String normalizedSourceText,
            String referenceMeaningsJson,
            String keyMeaningUnitsJson,
            String targetKeywordsJson,
            double estimatedAudioSeconds,
            String contentHash,
            String similarityKey,
            String generationMetadataJson,
            Long replacementForItemId,
            int replacementSequence
    ) {
        return new ListeningItem(
                dailySet,
                itemIndex,
                sourceText,
                normalizedSourceText,
                referenceMeaningsJson,
                keyMeaningUnitsJson,
                targetKeywordsJson,
                estimatedAudioSeconds,
                contentHash,
                similarityKey,
                generationMetadataJson,
                replacementForItemId,
                replacementSequence
        );
    }

    public void markTtsReady(
            String objectKey,
            int durationMs,
            String contentType,
            String checksum,
            String voiceSnapshotJson,
            LocalDateTime retentionUntil
    ) {
        this.audioObjectKey = objectKey;
        this.audioDurationMs = durationMs;
        this.audioContentType = contentType;
        this.audioChecksum = checksum;
        this.voiceSnapshotJson = voiceSnapshotJson;
        this.audioRetentionUntil = retentionUntil;
        this.audioDeletedAt = null;
        this.failureReason = null;
        this.status = ListeningItemStatus.READY;
    }

    public void registerAutomaticTtsRetry(String reason) {
        automaticTtsRetryCount++;
        failureReason = trim(reason);
    }

    public void startManualTtsRetry(int limit) {
        if (status != ListeningItemStatus.NOT_EVALUABLE
                || manualTtsRetryCount >= limit) {
            throw new IllegalStateException(
                    "수동 TTS 재시도 가능 횟수를 초과했습니다."
            );
        }
        manualTtsRetryCount++;
        status = ListeningItemStatus.TTS_PENDING;
        failureReason = null;
    }

    public void markNotEvaluable(String reason) {
        status = ListeningItemStatus.NOT_EVALUABLE;
        failureReason = trim(reason);
    }

    public void markReplaced() {
        status = ListeningItemStatus.REPLACED;
    }

    public boolean isPlayable(LocalDateTime now) {
        return status == ListeningItemStatus.READY
                && audioObjectKey != null
                && audioDeletedAt == null
                && (audioRetentionUntil == null
                || !audioRetentionUntil.isBefore(now));
    }

    public void markAudioDeleted(LocalDateTime now) {
        audioObjectKey = null;
        audioDeletedAt = now;
    }

    private static String json(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trim(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
