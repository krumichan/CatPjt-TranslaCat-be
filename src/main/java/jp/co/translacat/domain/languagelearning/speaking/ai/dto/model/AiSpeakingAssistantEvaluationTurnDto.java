package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import java.util.List;

public record AiSpeakingAssistantEvaluationTurnDto(
        String turnId,
        int turnIndex,
        String text,
        String scriptText,
        List<String> providedFacts,
        List<String> requiredIntents,
        List<String> responseConstraints
) {
}
