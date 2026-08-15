package jp.co.translacat.domain.languagelearning.speaking.turn.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingStage;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTurnStatus;

import java.time.LocalDateTime;

public record SpeakingTurnResponseDto(
        Long id,
        int turnIndex,
        SpeakingTurnStatus status,
        double durationSeconds,
        String transcript,
        Double sttConfidence,
        String assistantText,
        String assistantAudioUrl,
        boolean excludedFromEvaluation,
        SpeakingStage failedStage,
        String errorCode,
        String errorMessage,
        int manualRetryCount,
        LocalDateTime completedAt
) {
}
