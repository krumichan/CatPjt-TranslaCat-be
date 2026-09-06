package jp.co.translacat.domain.languagelearning.speaking.session.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.dto.SpeakingReadAloudProblemEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnResponseDto;

import java.util.List;

public record SpeakingSessionDetailResponseDto(
        SpeakingSessionResponseDto session,
        SpeakingDailyUsageResponseDto dailyUsage,
        List<SpeakingTurnResponseDto> turns,
        List<SpeakingReadAloudProblemEvaluationResponseDto> readAloudProblemEvaluations,
        SpeakingEvaluationEligibilityResponseDto evaluationEligibility,
        boolean resumable
) {
}
