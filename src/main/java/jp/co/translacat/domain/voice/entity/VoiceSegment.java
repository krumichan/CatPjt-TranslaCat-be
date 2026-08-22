package jp.co.translacat.domain.voice.entity;

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
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceSegmentStatus;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.jpa.Base;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Table(
        name = "voice_segment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_voice_segment_sequence",
                        columnNames = {
                                "session_id",
                                "channel",
                                "utterance_sequence"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_voice_segment_transcript_event",
                        columnNames = "transcript_final_event_id"
                ),
                @UniqueConstraint(
                        name = "uk_voice_segment_completed_event",
                        columnNames = "pipeline_completed_event_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_voice_segment_session",
                        columnList = "session_id,id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceSegment extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "session_id",
            nullable = false
    )
    private VoiceSession session;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 16
    )
    private VoiceChannel channel;

    @Column(
            name = "utterance_key",
            nullable = false,
            length = 120
    )
    private String utteranceKey;

    @Column(
            name = "utterance_sequence",
            nullable = false
    )
    private long utteranceSequence;

    @Column(
            name = "transcript_final_event_id",
            length = 100
    )
    private String transcriptFinalEventId;

    @Column(
            name = "pipeline_completed_event_id",
            length = 100
    )
    private String pipelineCompletedEventId;

    @Column(
            name = "started_at_offset_ms",
            nullable = false
    )
    private long startedAtOffsetMs;

    @Column(
            name = "ended_at_offset_ms",
            nullable = false
    )
    private long endedAtOffsetMs;

    @Column(
            name = "speech_duration_ms",
            nullable = false
    )
    private long speechDurationMs;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 24
    )
    private VoiceSegmentStatus status;

    @Column(
            name = "detected_language",
            length = 8
    )
    private String detectedLanguage;

    @Column(name = "language_confidence")
    private Double languageConfidence;

    @Column(
            name = "locked_language",
            length = 8
    )
    private String lockedLanguage;

    @Lob
    @Column(name = "source_text")
    private String sourceText;

    @Lob
    @Column(name = "source_reading_tokens")
    private String sourceReadingTokens;

    @Column(
            name = "target_language",
            nullable = false,
            length = 8
    )
    private String targetLanguage;

    @Lob
    @Column(name = "translated_text")
    private String translatedText;

    @Column(
            name = "translation_skipped",
            nullable = false
    )
    private boolean translationSkipped;

    @Column(
            name = "error_code",
            length = 100
    )
    private String errorCode;

    @Column(
            name = "retry_count",
            nullable = false
    )
    private int retryCount;

    @Column(name = "endpointing_ms")
    private Long endpointingMs;

    @Column(name = "stt_finalize_ms")
    private Long sttFinalizeMs;

    @Column(name = "translation_ms")
    private Long translationMs;

    @Column(name = "ai_total_after_speech_ms")
    private Long aiTotalAfterSpeechMs;

    @Column(name = "be_relay_and_persist_ms")
    private Long beRelayAndPersistMs;

    @Column(name = "total_after_speech_ms")
    private Long totalAfterSpeechMs;

    @Column(
            name = "stt_model_version",
            length = 200
    )
    private String sttModelVersion;

    @Column(
            name = "translation_model_version",
            length = 200
    )
    private String translationModelVersion;

    @Column(
            name = "prompt_version",
            length = 100
    )
    private String promptVersion;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    private long version;

    private VoiceSegment(
            VoiceSession session,
            VoiceChannel channel,
            String utteranceKey,
            long utteranceSequence,
            String transcriptFinalEventId,
            long startedAtOffsetMs,
            long endedAtOffsetMs,
            long speechDurationMs,
            String detectedLanguage,
            Double languageConfidence,
            String lockedLanguage,
            String sourceText
    ) {
        validateOffsets(
                startedAtOffsetMs,
                endedAtOffsetMs,
                speechDurationMs
        );

        this.session = session;
        this.channel = channel;
        this.utteranceKey = utteranceKey;
        this.utteranceSequence = utteranceSequence;
        this.transcriptFinalEventId = transcriptFinalEventId;
        this.startedAtOffsetMs = startedAtOffsetMs;
        this.endedAtOffsetMs = endedAtOffsetMs;
        this.speechDurationMs = speechDurationMs;
        this.detectedLanguage = detectedLanguage;
        this.languageConfidence = languageConfidence;
        this.lockedLanguage = lockedLanguage;
        this.sourceText = sourceText;
        this.targetLanguage = session.getTargetLanguage();
        this.status = VoiceSegmentStatus.TRANSLATING;
        this.translationSkipped = false;
        this.retryCount = 0;
    }

    public static VoiceSegment transcriptFinal(
            VoiceSession session,
            VoiceChannel channel,
            String utteranceKey,
            long utteranceSequence,
            String transcriptFinalEventId,
            long startedAtOffsetMs,
            long endedAtOffsetMs,
            long speechDurationMs,
            String detectedLanguage,
            Double languageConfidence,
            String lockedLanguage,
            String sourceText
    ) {
        return new VoiceSegment(
                session,
                channel,
                utteranceKey,
                utteranceSequence,
                transcriptFinalEventId,
                startedAtOffsetMs,
                endedAtOffsetMs,
                speechDurationMs,
                detectedLanguage,
                languageConfidence,
                lockedLanguage,
                sourceText
        );
    }

    public boolean matchesSource(String sourceText) {
        return Objects.equals(this.sourceText, sourceText);
    }

    public void applyCompleted(
            String completedEventId,
            String translatedText,
            String readingTokens,
            boolean translationSkipped,
            Long endpointingMs,
            Long sttFinalizeMs,
            Long translationMs,
            Long aiTotalAfterSpeechMs,
            Long beRelayAndPersistMs,
            String sttModelVersion,
            String translationModelVersion,
            String promptVersion
    ) {
        if (pipelineCompletedEventId != null
                && !pipelineCompletedEventId.equals(completedEventId)) {
            throw new BusinessException(
                    "Conflicting completed event for voice segment.",
                    VoiceErrorCode.FINAL_EVENT_CONFLICT
            );
        }

        pipelineCompletedEventId = completedEventId;
        this.translatedText = translatedText;
        sourceReadingTokens = readingTokens;
        this.translationSkipped = translationSkipped;
        errorCode = null;
        status = VoiceSegmentStatus.COMPLETED;
        this.endpointingMs = endpointingMs;
        this.sttFinalizeMs = sttFinalizeMs;
        this.translationMs = translationMs;
        this.aiTotalAfterSpeechMs = aiTotalAfterSpeechMs;
        this.beRelayAndPersistMs = beRelayAndPersistMs;
        totalAfterSpeechMs = safeAdd(
                aiTotalAfterSpeechMs,
                beRelayAndPersistMs
        );
        this.sttModelVersion = sttModelVersion;
        this.translationModelVersion = translationModelVersion;
        this.promptVersion = promptVersion;
        completedAt = LocalDateTime.now();
    }

    public void updateRelayLatency(long beRelayAndPersistMs) {
        this.beRelayAndPersistMs = Math.max(
                0L,
                beRelayAndPersistMs
        );
        totalAfterSpeechMs = safeAdd(
                aiTotalAfterSpeechMs,
                this.beRelayAndPersistMs
        );
    }

    public void markTranslationFailed(String errorCode) {
        this.errorCode = errorCode;
        status = VoiceSegmentStatus.TRANSLATION_FAILED;
        completedAt = LocalDateTime.now();
    }

    public void applyRetry(
            String translatedText,
            String readingTokens,
            boolean translationSkipped,
            String translationModelVersion,
            String promptVersion
    ) {
        retryCount++;
        this.translatedText = translatedText;
        sourceReadingTokens = readingTokens;
        this.translationSkipped = translationSkipped;
        this.translationModelVersion = translationModelVersion;
        this.promptVersion = promptVersion;
        errorCode = null;
        status = VoiceSegmentStatus.COMPLETED;
        completedAt = LocalDateTime.now();
    }

    private static void validateOffsets(
            long startedAtOffsetMs,
            long endedAtOffsetMs,
            long speechDurationMs
    ) {
        if (startedAtOffsetMs < 0
                || endedAtOffsetMs < startedAtOffsetMs
                || speechDurationMs < 0) {
            throw new BusinessException(
                    "Invalid voice segment offsets.",
                    VoiceErrorCode.INVALID_SEGMENT_OFFSET
            );
        }
    }

    private static Long safeAdd(
            Long first,
            Long second
    ) {
        if (first == null && second == null) {
            return null;
        }

        return (first == null ? 0L : first)
                + (second == null ? 0L : second);
    }
}
