package jp.co.translacat.domain.languagelearning.speaking.evaluation.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_speaking_evaluation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_speaking_eval_session_version",
                columnNames = {"session_id", "evaluation_version"}
        ),
        indexes = @Index(
                name = "idx_ll_speaking_eval_session",
                columnList = "session_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakingEvaluation extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private SpeakingSession session;

    private Integer overallScore;
    private Double evaluationConfidence;

    @Column(name = "evaluation_version", nullable = false, length = 100)
    private String evaluationVersion;

    @Column(name = "scoring_policy_version", nullable = false, length = 100)
    private String scoringPolicyVersion;

    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    @Column(nullable = false, length = 40)
    private String status;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String strengthsJson = "[]";

    @Lob
    @Column(columnDefinition = "TEXT")
    private String improvementsJson = "[]";

    @Lob
    @Column(name = "recommended_expressions_json", columnDefinition = "TEXT")
    private String recommendedExpressionsJson = "[]";

    @Lob
    @Column(name = "pronunciation_practice_json", columnDefinition = "TEXT")
    private String pronunciationPracticeJson = "[]";

    @Lob
    @Column(name = "profile_signals_json", columnDefinition = "TEXT")
    private String profileSignalsJson = "[]";

    @Lob
    @Column(name = "eligibility_json", columnDefinition = "TEXT")
    private String eligibilityJson = "{}";

    @Lob
    @Column(name = "usage_json", columnDefinition = "TEXT")
    private String usageJson = "{}";

    @Column(nullable = false)
    private LocalDateTime evaluatedAt;

    private SpeakingEvaluation(
            SpeakingSession session,
            Integer overallScore,
            Double evaluationConfidence,
            String evaluationVersion,
            String scoringPolicyVersion,
            String promptVersion,
            String status,
            String strengthsJson,
            String improvementsJson,
            String recommendedExpressionsJson,
            String pronunciationPracticeJson,
            String profileSignalsJson,
            String eligibilityJson,
            String usageJson
    ) {
        this.session = session;
        this.overallScore = overallScore;
        this.evaluationConfidence = evaluationConfidence;
        this.evaluationVersion = evaluationVersion;
        this.scoringPolicyVersion = scoringPolicyVersion;
        this.promptVersion = promptVersion;
        this.status = status;
        this.strengthsJson = strengthsJson;
        this.improvementsJson = improvementsJson;
        this.recommendedExpressionsJson = recommendedExpressionsJson;
        this.pronunciationPracticeJson = pronunciationPracticeJson;
        this.profileSignalsJson = profileSignalsJson;
        this.eligibilityJson = eligibilityJson;
        this.usageJson = usageJson;
        this.evaluatedAt = LocalDateTime.now();
    }

    public static SpeakingEvaluation create(
            SpeakingSession session,
            Integer overallScore,
            Double evaluationConfidence,
            String evaluationVersion,
            String scoringPolicyVersion,
            String promptVersion,
            String status,
            String strengthsJson,
            String improvementsJson,
            String recommendedExpressionsJson,
            String pronunciationPracticeJson,
            String profileSignalsJson,
            String eligibilityJson,
            String usageJson
    ) {
        return new SpeakingEvaluation(
                session,
                overallScore,
                evaluationConfidence,
                evaluationVersion,
                scoringPolicyVersion,
                promptVersion,
                status,
                strengthsJson,
                improvementsJson,
                recommendedExpressionsJson,
                pronunciationPracticeJson,
                profileSignalsJson,
                eligibilityJson,
                usageJson
        );
    }
}
