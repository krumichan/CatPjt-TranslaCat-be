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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.voice.enums.VoiceUsageType;
import jp.co.translacat.global.jpa.Base;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Table(
        name = "voice_usage_ledger",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_voice_usage_segment",
                        columnNames = "segment_id"
                ),
                @UniqueConstraint(
                        name = "uk_voice_usage_idempotency",
                        columnNames = "idempotency_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_voice_usage_user_date",
                        columnList = "user_id,usage_date"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceUsageLedger extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Column(
            name = "session_id",
            nullable = false,
            length = 36
    )
    private String sessionId;

    @Column(
            name = "segment_id",
            nullable = false
    )
    private Long segmentId;

    @Column(
            name = "usage_date",
            nullable = false
    )
    private LocalDate usageDate;

    @Column(
            name = "processed_audio_ms",
            nullable = false
    )
    private long processedAudioMs;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "usage_type",
            nullable = false,
            length = 24
    )
    private VoiceUsageType usageType;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 160
    )
    private String idempotencyKey;

    private VoiceUsageLedger(
            User user,
            VoiceSegment segment
    ) {
        this.user = user;
        sessionId = segment.getSession().getId();
        segmentId = segment.getId();
        usageDate = LocalDate.now();
        processedAudioMs = segment.getSpeechDurationMs();
        usageType = VoiceUsageType.VOICE_STREAM;
        idempotencyKey = "voice-segment:" + segment.getId();
    }

    public static VoiceUsageLedger create(
            User user,
            VoiceSegment segment
    ) {
        return new VoiceUsageLedger(
                user,
                segment
        );
    }
}
