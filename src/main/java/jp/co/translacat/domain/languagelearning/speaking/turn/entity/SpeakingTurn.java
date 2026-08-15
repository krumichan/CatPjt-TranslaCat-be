package jp.co.translacat.domain.languagelearning.speaking.turn.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingStage;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTurnStatus;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_speaking_turn",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ll_speaking_turn_session_index",
                        columnNames = {"session_id", "turn_index"}
                ),
                @UniqueConstraint(
                        name = "uk_ll_speaking_turn_session_idempotency",
                        columnNames = {"session_id", "idempotency_key"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_ll_speaking_turn_session",
                        columnList = "session_id,turn_index"
                ),
                @Index(
                        name = "idx_ll_speaking_turn_user_audio_retention",
                        columnList = "user_audio_retention_until"
                ),
                @Index(
                        name = "idx_ll_speaking_turn_assistant_audio_retention",
                        columnList = "assistant_audio_retention_until"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakingTurn extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private SpeakingSession session;

    @Column(name = "turn_index", nullable = false)
    private int turnIndex;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SpeakingTurnStatus status;

    @Column(name = "upload_token", nullable = false, length = 100)
    private String uploadToken;

    @Column(name = "upload_expires_at", nullable = false)
    private LocalDateTime uploadExpiresAt;

    @Column(name = "user_audio_object_key", length = 500)
    private String userAudioObjectKey;

    @Column(name = "user_audio_content_type", length = 100)
    private String userAudioContentType;

    @Column(name = "user_audio_file_name", length = 300)
    private String userAudioFileName;

    private double durationSeconds;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String transcript;

    private Double sttConfidence;

    @Lob
    @Column(name = "stt_segments_json", columnDefinition = "TEXT")
    private String sttSegmentsJson = "[]";

    @Lob
    @Column(name = "stt_metadata_json", columnDefinition = "TEXT")
    private String sttMetadataJson = "{}";

    @Column(name = "assistant_text", length = 4000)
    private String assistantText;

    @Column(name = "assistant_audio_object_key", length = 500)
    private String assistantAudioObjectKey;

    @Column(name = "assistant_audio_content_type", length = 100)
    private String assistantAudioContentType;

    @Lob
    @Column(name = "conversation_json", columnDefinition = "TEXT")
    private String conversationJson = "{}";

    @Column(nullable = false)
    private boolean excludedFromEvaluation;

    @Lob
    @Column(name = "assistance_usage_json", nullable = false, columnDefinition = "TEXT")
    private String assistanceUsageJson = "[]";

    @Enumerated(EnumType.STRING)
    @Column(name = "failed_stage", length = 40)
    private SpeakingStage failedStage;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private int manualRetryCount;

    @Lob
    @Column(name = "usage_json", nullable = false, columnDefinition = "TEXT")
    private String usageJson = "{}";

    @Column(name = "user_audio_retention_until")
    private LocalDateTime userAudioRetentionUntil;

    @Column(name = "assistant_audio_retention_until")
    private LocalDateTime assistantAudioRetentionUntil;

    private LocalDateTime completedAt;

    private SpeakingTurn(
            SpeakingSession session,
            int turnIndex,
            String idempotencyKey,
            String uploadToken,
            LocalDateTime uploadExpiresAt
    ) {
        this.session = session;
        this.turnIndex = turnIndex;
        this.idempotencyKey = idempotencyKey;
        this.uploadToken = uploadToken;
        this.uploadExpiresAt = uploadExpiresAt;
        this.status = SpeakingTurnStatus.AWAITING_UPLOAD;
    }

    public static SpeakingTurn createUploadGrant(
            SpeakingSession session,
            int turnIndex,
            String idempotencyKey,
            String uploadToken,
            LocalDateTime uploadExpiresAt
    ) {
        return new SpeakingTurn(
                session,
                turnIndex,
                idempotencyKey,
                uploadToken,
                uploadExpiresAt
        );
    }

    public boolean isUploadTokenValid(
            String token,
            LocalDateTime now
    ) {
        return uploadToken.equals(token)
                && !uploadExpiresAt.isBefore(now);
    }

    public void markUploaded(
            String objectKey,
            String contentType,
            String fileName,
            double durationSeconds,
            String assistanceUsageJson,
            LocalDateTime audioRetentionUntil
    ) {
        this.userAudioObjectKey = objectKey;
        this.userAudioContentType = contentType;
        this.userAudioFileName = fileName;
        this.durationSeconds = durationSeconds;
        this.assistanceUsageJson = assistanceUsageJson == null
                ? "[]"
                : assistanceUsageJson;
        this.userAudioRetentionUntil = audioRetentionUntil;
        this.status = SpeakingTurnStatus.UPLOADED;
        clearError();
    }

    public void markProcessing() {
        this.status = SpeakingTurnStatus.PROCESSING;
        clearError();
    }

    public void applyTranscript(
            String transcript,
            Double confidence,
            String segmentsJson,
            String metadataJson
    ) {
        this.transcript = transcript;
        this.sttConfidence = confidence;
        this.sttSegmentsJson = segmentsJson == null ? "[]" : segmentsJson;
        this.sttMetadataJson = metadataJson == null ? "{}" : metadataJson;
    }

    public void applyAssistant(
            String assistantText,
            String assistantAudioObjectKey,
            String assistantAudioContentType,
            String conversationJson
    ) {
        this.assistantText = assistantText;
        this.assistantAudioObjectKey = assistantAudioObjectKey;
        this.assistantAudioContentType = assistantAudioContentType;
        this.conversationJson = conversationJson == null
                ? "{}"
                : conversationJson;
    }

    public void markReady(String usageJson) {
        this.status = SpeakingTurnStatus.READY;
        this.usageJson = usageJson == null ? "{}" : usageJson;
        this.completedAt = LocalDateTime.now();
        clearError();
    }

    public void markPartialFailure(
            SpeakingStage failedStage,
            String errorCode,
            String errorMessage,
            String usageJson
    ) {
        this.status = SpeakingTurnStatus.PARTIAL_FAILURE;
        this.failedStage = failedStage;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.usageJson = usageJson == null ? "{}" : usageJson;
    }

    public void markFailed(
            SpeakingStage failedStage,
            String errorCode,
            String errorMessage
    ) {
        this.status = SpeakingTurnStatus.FAILED;
        this.failedStage = failedStage;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public void incrementManualRetry() {
        this.manualRetryCount++;
    }

    public void exclude() {
        this.excludedFromEvaluation = true;
        if (this.status == SpeakingTurnStatus.READY) {
            this.status = SpeakingTurnStatus.EXCLUDED;
        }
    }

    public void restoreReadyAfterExcludeToggle() {
        this.excludedFromEvaluation = false;
        if (this.status == SpeakingTurnStatus.EXCLUDED) {
            this.status = SpeakingTurnStatus.READY;
        }
    }

    public void extendUserAudioRetention(LocalDateTime retentionUntil) {
        if (retentionUntil == null) {
            return;
        }
        if (this.userAudioRetentionUntil == null
                || this.userAudioRetentionUntil.isBefore(retentionUntil)) {
            this.userAudioRetentionUntil = retentionUntil;
        }
    }

    public void setAssistantAudioRetention(LocalDateTime retentionUntil) {
        this.assistantAudioRetentionUntil = retentionUntil;
    }

    public void clearUserAudio() {
        this.userAudioObjectKey = null;
        this.userAudioRetentionUntil = null;
    }

    public void clearAssistantAudio() {
        this.assistantAudioObjectKey = null;
        this.assistantAudioRetentionUntil = null;
    }

    private void clearError() {
        this.failedStage = null;
        this.errorCode = null;
        this.errorMessage = null;
    }
}
