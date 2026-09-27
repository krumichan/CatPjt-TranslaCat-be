package jp.co.translacat.domain.languagelearning.speaking.session.dto.response;

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
}
