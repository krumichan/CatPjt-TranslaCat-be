package jp.co.translacat.domain.languagelearning.speaking.evaluation.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.MetricEvaluationState;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "language_learning_speaking_evaluation_metric",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_speaking_eval_metric",
                columnNames = {"evaluation_id", "metric_type"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakingEvaluationMetric extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false, updatable = false)
    private SpeakingEvaluation evaluation;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 40)
    private SpeakingMetricType metricType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MetricEvaluationState state;

    private Double score;
    private double confidence;

    @Column(length = 2000)
    private String summary;

    @Column(name = "not_evaluable_reason", length = 1000)
    private String notEvaluableReason;

    @Lob
    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson = "[]";

    private SpeakingEvaluationMetric(
            SpeakingEvaluation evaluation,
            SpeakingMetricType metricType,
            MetricEvaluationState state,
            Double score,
            double confidence,
            String summary,
            String notEvaluableReason,
            String evidenceJson
    ) {
        this.evaluation = evaluation;
        this.metricType = metricType;
        this.state = state;
        this.score = score;
        this.confidence = confidence;
        this.summary = summary;
        this.notEvaluableReason = notEvaluableReason;
        this.evidenceJson = evidenceJson;
    }

    public static SpeakingEvaluationMetric create(
            SpeakingEvaluation evaluation,
            SpeakingMetricType metricType,
            MetricEvaluationState state,
            Double score,
            double confidence,
            String summary,
            String notEvaluableReason,
            String evidenceJson
    ) {
        return new SpeakingEvaluationMetric(
                evaluation,
                metricType,
                state,
                score,
                confidence,
                summary,
                notEvaluableReason,
                evidenceJson
        );
    }
}
