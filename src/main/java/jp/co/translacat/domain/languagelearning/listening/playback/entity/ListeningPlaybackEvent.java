package jp.co.translacat.domain.languagelearning.listening.playback.entity;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningPlaybackType;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_listening_playback_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_listening_playback_client_event",
                columnNames = {"attempt_id", "client_event_id"}
        ),
        indexes = @Index(
                name = "idx_ll_listening_playback_attempt",
                columnList = "attempt_id,occurred_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningPlaybackEvent extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false)
    private ListeningItemAttempt attempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "playback_type", nullable = false, length = 20)
    private ListeningPlaybackType playbackType;

    @Column(name = "client_event_id", nullable = false, length = 200)
    private String clientEventId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    private ListeningPlaybackEvent(
            ListeningItemAttempt attempt,
            ListeningPlaybackType playbackType,
            String clientEventId,
            LocalDateTime occurredAt
    ) {
        this.attempt = attempt;
        this.playbackType = playbackType;
        this.clientEventId = clientEventId;
        this.occurredAt = occurredAt;
    }

    public static ListeningPlaybackEvent create(
            ListeningItemAttempt attempt,
            ListeningPlaybackType playbackType,
            String clientEventId,
            LocalDateTime occurredAt
    ) {
        return new ListeningPlaybackEvent(
                attempt,
                playbackType,
                clientEventId,
                occurredAt
        );
    }
}
