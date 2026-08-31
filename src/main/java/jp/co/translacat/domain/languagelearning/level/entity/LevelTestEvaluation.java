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
        name = "language_learning_level_test_evaluation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_level_eval_response",
                columnNames = "response_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelTestEvaluation extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false, updatable = false)
    private LevelTestResponse response;

    @Column(nullable = false)
    private boolean evaluable;

    private Integer score;

    private Double confidence;

    @Lob
    @Column(name = "metrics_json", nullable = false, columnDefinition = "TEXT")
    private String metricsJson;

    @Lob
    @Column(name = "strengths_json", nullable = false, columnDefinition = "TEXT")
    private String strengthsJson;

    @Lob
    @Column(name = "improvements_json", nullable = false, columnDefinition = "TEXT")
    private String improvementsJson;

    @Lob
    @Column(
            name = "assessment_signals_json",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String assessmentSignalsJson;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "evaluation_version", nullable = false, length = 100)
    private String evaluationVersion;

    @Column(name = "manual_retry_count", nullable = false)
    private int manualRetryCount;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    private LevelTestEvaluation(
            LevelTestResponse response,
            boolean evaluable,
            Integer score,
            Double confidence,
            String metricsJson,
            String strengthsJson,
            String improvementsJson,
            String assessmentSignalsJson,
            String reasonCode,
            String evaluationVersion,
            int manualRetryCount,
            LocalDateTime evaluatedAt
    ) {
        this.response = response;
        replace(
                evaluable,
                score,
                confidence,
                metricsJson,
                strengthsJson,
                improvementsJson,
                assessmentSignalsJson,
                reasonCode,
                evaluationVersion,
                manualRetryCount,
                evaluatedAt
        );
    }

    public static LevelTestEvaluation create(
            LevelTestResponse response,
            boolean evaluable,
            Integer score,
            Double confidence,
            String metricsJson,
            String strengthsJson,
            String improvementsJson,
            String assessmentSignalsJson,
            String reasonCode,
            String evaluationVersion,
            int manualRetryCount,
            LocalDateTime evaluatedAt
    ) {
        return new LevelTestEvaluation(
                response,
                evaluable,
                score,
                confidence,
                metricsJson,
                strengthsJson,
                improvementsJson,
                assessmentSignalsJson,
                reasonCode,
                evaluationVersion,
                manualRetryCount,
                evaluatedAt
        );
    }

    public void replace(
            boolean evaluable,
            Integer score,
            Double confidence,
            String metricsJson,
            String strengthsJson,
            String improvementsJson,
            String assessmentSignalsJson,
            String reasonCode,
            String evaluationVersion,
            int manualRetryCount,
            LocalDateTime evaluatedAt
    ) {
        this.evaluable = evaluable;
        this.score = score;
        this.confidence = confidence;
        this.metricsJson = metricsJson;
        this.strengthsJson = strengthsJson;
        this.improvementsJson = improvementsJson;
        this.assessmentSignalsJson = assessmentSignalsJson;
        this.reasonCode = reasonCode;
        this.evaluationVersion = evaluationVersion;
        this.manualRetryCount = manualRetryCount;
        this.evaluatedAt = evaluatedAt;
    }
}
