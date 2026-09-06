package jp.co.translacat.domain.languagelearning.speaking.session.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationStatus;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingSessionStatus;

public record SpeakingPracticeModeStatusResponseDto(
        SpeakingPracticeMode practiceMode,
        Long sessionId,
        SpeakingSessionStatus sessionStatus,
        SpeakingEvaluationStatus evaluationStatus,
        int completedTurns,
        int maxTurns,
        boolean completed
) {
}
