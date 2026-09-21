package jp.co.translacat.domain.languagelearning.speaking.session.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationStatus;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingSessionStatus;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingResultKind;

public record SpeakingPracticeModeStatusResponseDto(
        SpeakingPracticeMode practiceMode,
        Long sessionId,
        SpeakingSessionStatus sessionStatus,
        SpeakingEvaluationStatus evaluationStatus,
        SpeakingResultKind resultKind,
        String resultPolicyVersion,
        String resultStatus,
        int completedTurns,
        int maxTurns,
        boolean completed
) {
}
