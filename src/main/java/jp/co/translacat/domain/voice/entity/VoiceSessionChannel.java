package jp.co.translacat.domain.voice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceChannelStatus;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "voice_session_channel",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_voice_session_channel",
                        columnNames = {
                                "session_id",
                                "channel"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceSessionChannel extends BaseAuditable {

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

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private VoiceChannelStatus status;

    @Column(
            name = "manual_source_language",
            length = 8
    )
    private String manualSourceLanguage;

    @Column(
            name = "last_locked_language",
            length = 8
    )
    private String lastLockedLanguage;

    @Column(
            name = "active_connection_id",
            length = 36
    )
    private String activeConnectionId;

    @Column(
            name = "reconnect_count",
            nullable = false
    )
    private int reconnectCount;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Version
    private long version;

    private VoiceSessionChannel(
            VoiceSession session,
            VoiceChannel channel,
            String manualSourceLanguage
    ) {
        if (!session.allows(channel)) {
            throw new BusinessException(
                    "Channel is not allowed for voice mode.",
                    VoiceErrorCode.CHANNEL_NOT_ALLOWED
            );
        }

        this.session = session;
        this.channel = channel;
        this.manualSourceLanguage = manualSourceLanguage;
        this.status = VoiceChannelStatus.DISCONNECTED;
    }

    public static VoiceSessionChannel create(
            VoiceSession session,
            VoiceChannel channel,
            String manualSourceLanguage
    ) {
        return new VoiceSessionChannel(
                session,
                channel,
                manualSourceLanguage
        );
    }

    public void connecting(String connectionId) {
        if (activeConnectionId != null) {
            throw new BusinessException(
                    "Voice channel already has an active connection.",
                    VoiceErrorCode.CHANNEL_ALREADY_CONNECTED
            );
        }

        if (connectedAt != null) {
            reconnectCount++;
        }

        activeConnectionId = connectionId;
        status = VoiceChannelStatus.CONNECTING;
    }

    public void streaming(String connectionId) {
        requireConnection(connectionId);

        status = VoiceChannelStatus.STREAMING;
        connectedAt = LocalDateTime.now();
        disconnectedAt = null;
    }

    public void backpressured(String connectionId) {
        requireConnection(connectionId);
        status = VoiceChannelStatus.BACKPRESSURED;
    }

    public void resumed(String connectionId) {
        requireConnection(connectionId);
        status = VoiceChannelStatus.STREAMING;
    }

    public void reconnecting(String connectionId) {
        requireConnection(connectionId);

        status = VoiceChannelStatus.RECONNECTING;
        activeConnectionId = null;
        disconnectedAt = LocalDateTime.now();
    }

    public void error(String connectionId) {
        requireConnection(connectionId);

        status = VoiceChannelStatus.ERROR;
        activeConnectionId = null;
        disconnectedAt = LocalDateTime.now();
    }

    public void disconnected(String connectionId) {
        if (activeConnectionId == null
                || !activeConnectionId.equals(connectionId)) {
            return;
        }

        status = VoiceChannelStatus.DISCONNECTED;
        activeConnectionId = null;
        disconnectedAt = LocalDateTime.now();
    }

    public void lockLanguage(String language) {
        lastLockedLanguage = language;
    }

    private void requireConnection(String connectionId) {
        if (activeConnectionId == null
                || !activeConnectionId.equals(connectionId)) {
            throw new BusinessException(
                    "Stale voice connection.",
                    VoiceErrorCode.STALE_CONNECTION
            );
        }
    }
}
