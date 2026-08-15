package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import java.util.List;

public record AiSpeakingRecommendedExpressionDto(
        String original,
        String recommended,
        String explanation,
        List<String> evidenceTurnIds
) {
}
