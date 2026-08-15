package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;

import java.util.List;

public record AiSpeakingMetricDto(
        SpeakingMetricType type,
        String state,
        Double score,
        double confidence,
        String summary,
        List<AiSpeakingMetricEvidenceDto> evidence,
        String notEvaluableReason
) {
}
