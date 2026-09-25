package jp.co.translacat.domain.languagelearning.growth.model;

import jp.co.translacat.domain.languagelearning.common.enums.MetricEvaluationState;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;

public record GrowthMetricSnapshot(SpeakingMetricType metricType, MetricEvaluationState state,
                                   Double score, Double confidence, String notEvaluableReason) {
}
