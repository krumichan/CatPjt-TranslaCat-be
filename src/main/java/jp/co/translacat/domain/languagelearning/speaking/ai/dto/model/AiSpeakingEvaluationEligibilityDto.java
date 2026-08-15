package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import java.util.List;

public record AiSpeakingEvaluationEligibilityDto(
        int validUserTurns,
        double validUserSpeechSeconds,
        double validSttTurnRatio,
        int requiredUserTurns,
        double requiredSpeechSeconds,
        double requiredSttTurnRatio,
        double requiredEvaluationConfidence,
        boolean eligibleBeforeAi,
        List<String> missingRequirements
) {
}
