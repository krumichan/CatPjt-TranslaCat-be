package jp.co.translacat.domain.languagelearning.speaking.evaluation.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.MetricEvaluationState;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;

public record SpeakingMetricResponseDto(
        SpeakingMetricType metricType,
        MetricEvaluationState state,
        Double score,
        double confidence,
        String summary,
        String notEvaluableReason,
        String evidenceJson
) {
}
