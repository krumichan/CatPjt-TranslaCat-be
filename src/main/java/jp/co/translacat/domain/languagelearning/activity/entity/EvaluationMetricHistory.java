package jp.co.translacat.domain.languagelearning.activity.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.MetricEvaluationState;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "language_learning_metric_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_metric_history_activity_metric",
                columnNames = {"activity_id", "metric_type"}
        ),
        indexes = @Index(
                name = "idx_ll_metric_history_metric",
                columnList = "metric_type"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationMetricHistory extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false, updatable = false)
    private LearningActivity activity;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 40)
    private WritingMetric metricType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MetricEvaluationState state;

    private Double score;
    private Double confidence;

    @Column(length = 1000)
    private String notEvaluableReason;

    private EvaluationMetricHistory(
            LearningActivity activity,
            WritingMetric metricType,
            MetricEvaluationState state,
            Double score,
            Double confidence,
            String notEvaluableReason
    ) {
        this.activity = activity;
        this.metricType = metricType;
        this.state = state;
        this.score = score;
        this.confidence = confidence;
        this.notEvaluableReason = notEvaluableReason;
    }

    public static EvaluationMetricHistory create(
            LearningActivity activity,
            WritingMetric metricType,
            MetricEvaluationState state,
            Double score,
            Double confidence,
            String notEvaluableReason
    ) {
        return new EvaluationMetricHistory(
                activity,
                metricType,
                state,
                score,
                confidence,
                notEvaluableReason
        );
    }
}
