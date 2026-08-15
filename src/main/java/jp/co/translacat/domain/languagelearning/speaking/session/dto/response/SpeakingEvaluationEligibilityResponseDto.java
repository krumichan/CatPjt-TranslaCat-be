package jp.co.translacat.domain.languagelearning.speaking.session.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingEvaluationEligibilityDto;

import java.util.List;

public record SpeakingEvaluationEligibilityResponseDto(
        int validUserTurns,
        double validUserSpeechSeconds,
        double validSttTurnRatio,
        int requiredUserTurns,
        double requiredUserSpeechSeconds,
        double requiredSttTurnRatio,
        double requiredEvaluationConfidence,
        boolean eligible,
        List<String> missingRequirements
) {
    public static SpeakingEvaluationEligibilityResponseDto from(
            AiSpeakingEvaluationEligibilityDto value
    ) {
        return new SpeakingEvaluationEligibilityResponseDto(
                value.validUserTurns(),
                value.validUserSpeechSeconds(),
                value.validSttTurnRatio(),
                value.requiredUserTurns(),
                value.requiredSpeechSeconds(),
                value.requiredSttTurnRatio(),
                value.requiredEvaluationConfidence(),
                value.eligibleBeforeAi(),
                value.missingRequirements()
        );
    }
}
