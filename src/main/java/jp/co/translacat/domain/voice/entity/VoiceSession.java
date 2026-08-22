package jp.co.translacat.domain.voice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceMode;
import jp.co.translacat.domain.voice.enums.VoiceSessionStatus;
import jp.co.translacat.domain.voice.enums.VoiceSourceLanguageMode;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Entity
@Getter
@Table(
        name = "voice_session",
        indexes = {
                @Index(
                        name = "idx_voice_session_user_status",
                        columnList = "user_id,status"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceSession extends BaseAuditable {

    @Id
    @Column(
            length = 36,
            nullable = false,
            updatable = false
    )
    private String id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 16
    )
    private VoiceMode mode;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "source_language_mode",
            nullable = false,
            length = 16
    )
    private VoiceSourceLanguageMode sourceLanguageMode;

    @Column(
            name = "target_language",
            nullable = false,
            length = 8
    )
    private String targetLanguage;

    @Column(
            name = "save_transcript",
            nullable = false
    )
    private boolean saveTranscript;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 16
    )
    private VoiceSessionStatus status;

    @Column(
            nullable = false,
            length = 200
    )
    private String title;

    @Lob
    @Column(
            name = "policy_snapshot",
            nullable = false
    )
    private String policySnapshot;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completing_at")
    private LocalDateTime completingAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(
            name = "processed_audio_ms",
            nullable = false
    )
    private long processedAudioMs;

    @Version
    private long version;

    private VoiceSession(
            User user,
            VoiceMode mode,
            VoiceSourceLanguageMode sourceLanguageMode,
            String targetLanguage,
            boolean saveTranscript,
            String policySnapshot
    ) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.mode = mode;
        this.sourceLanguageMode = sourceLanguageMode;
        this.targetLanguage = targetLanguage;
        this.saveTranscript = saveTranscript;
        this.status = VoiceSessionStatus.CREATED;
        this.title = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + " 기록";
        this.policySnapshot = policySnapshot;
        this.processedAudioMs = 0L;
        this.createdBy = user.getEmail();
    }

    public static VoiceSession create(
            User user,
            VoiceMode mode,
            VoiceSourceLanguageMode sourceLanguageMode,
            String targetLanguage,
            boolean saveTranscript,
            String policySnapshot
    ) {
        return new VoiceSession(
                user,
                mode,
                sourceLanguageMode,
                targetLanguage,
                saveTranscript,
                policySnapshot
        );
    }

    public boolean allows(VoiceChannel channel) {
        return mode.allowedChannels().contains(channel);
    }

    public void updateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(
                    "Voice session title must not be blank.",
                    VoiceErrorCode.INVALID_TITLE
            );
        }

        this.title = title.trim();
    }

    public void updateConnectivity(int streamingChannels) {
        if (!status.isOpen()) {
            return;
        }

        int requiredChannelCount = mode.allowedChannels().size();

        if (streamingChannels >= requiredChannelCount) {
            status = VoiceSessionStatus.ACTIVE;
            markStarted();
            return;
        }

        if (streamingChannels > 0 || startedAt != null) {
            status = VoiceSessionStatus.DEGRADED;
            markStarted();
            return;
        }

        status = VoiceSessionStatus.CREATED;
    }

    public void startCompleting() {
        if (status == VoiceSessionStatus.COMPLETING) {
            return;
        }

        if (!status.isOpen()) {
            throw new BusinessException(
                    "Voice session cannot complete from status " + status,
                    VoiceErrorCode.INVALID_STATE
            );
        }

        status = VoiceSessionStatus.COMPLETING;
        completingAt = LocalDateTime.now();
    }

    public void complete() {
        if (status == VoiceSessionStatus.COMPLETED) {
            return;
        }

        if (status != VoiceSessionStatus.COMPLETING) {
            throw new BusinessException(
                    "Voice session is not completing.",
                    VoiceErrorCode.INVALID_STATE
            );
        }

        status = VoiceSessionStatus.COMPLETED;
        completedAt = LocalDateTime.now();
    }

    public void fail() {
        if (status == VoiceSessionStatus.COMPLETED
                || status == VoiceSessionStatus.FAILED) {
            return;
        }

        status = VoiceSessionStatus.FAILED;
        completedAt = LocalDateTime.now();
    }

    public void addProcessedAudioMs(long amount) {
        if (amount < 0) {
            throw new BusinessException(
                    "Processed audio duration must be non-negative.",
                    VoiceErrorCode.INVALID_USAGE
            );
        }

        processedAudioMs += amount;
    }

    private void markStarted() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
