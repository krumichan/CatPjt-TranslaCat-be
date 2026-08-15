package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;

import java.util.List;

public record AiSpeakingProfileSignalDto(
        SpeakingMetricType metricType,
        String source,
        String direction,
        double confidence,
        List<String> evidenceTurnIds,
        String patternKey,
        String recommendedFocus
) {
}
