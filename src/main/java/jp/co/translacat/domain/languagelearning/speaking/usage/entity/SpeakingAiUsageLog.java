package jp.co.translacat.domain.languagelearning.speaking.usage.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingStage;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "language_learning_speaking_ai_usage",
        indexes = {
                @Index(
                        name = "idx_ll_speaking_usage_session",
                        columnList = "session_id"
                ),
                @Index(
                        name = "idx_ll_speaking_usage_stage",
                        columnList = "stage"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakingAiUsageLog extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private SpeakingSession session;

    private Long turnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SpeakingStage stage;

    private int latencyMs;
    private int inputTokens;
    private int outputTokens;
    private double audioSeconds;
    private int ttsCharacters;
    private double ttsAudioSeconds;

    @Column(length = 100)
    private String provider;

    @Column(length = 200)
    private String model;

    @Column(length = 100)
    private String promptVersion;

    @Column(length = 100)
    private String evaluationVersion;

    private int manualRetryAttempt;

    private SpeakingAiUsageLog(
            SpeakingSession session,
            Long turnId,
            SpeakingStage stage,
            int latencyMs,
            int inputTokens,
            int outputTokens,
            double audioSeconds,
            int ttsCharacters,
            double ttsAudioSeconds,
            String provider,
            String model,
            String promptVersion,
            String evaluationVersion,
            int manualRetryAttempt
    ) {
        this.session = session;
        this.turnId = turnId;
        this.stage = stage;
        this.latencyMs = latencyMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.audioSeconds = audioSeconds;
        this.ttsCharacters = ttsCharacters;
        this.ttsAudioSeconds = ttsAudioSeconds;
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
        this.evaluationVersion = evaluationVersion;
        this.manualRetryAttempt = manualRetryAttempt;
    }

    public static SpeakingAiUsageLog create(
            SpeakingSession session,
            Long turnId,
            SpeakingStage stage,
            int latencyMs,
            int inputTokens,
            int outputTokens,
            double audioSeconds,
            int ttsCharacters,
            double ttsAudioSeconds,
            String provider,
            String model,
            String promptVersion,
            String evaluationVersion,
            int manualRetryAttempt
    ) {
        return new SpeakingAiUsageLog(
                session,
                turnId,
                stage,
                latencyMs,
                inputTokens,
                outputTokens,
                audioSeconds,
                ttsCharacters,
                ttsAudioSeconds,
                provider,
                model,
                promptVersion,
                evaluationVersion,
                manualRetryAttempt
        );
    }
}
