package jp.co.translacat.domain.languagelearning.speaking.turn.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingStage;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTurnStatus;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingPromptGuideResponseDto;

import java.time.LocalDateTime;
import java.util.List;

public record SpeakingTurnResponseDto(
        Long id,
        int turnIndex,
        Integer problemIndex,
        Integer attemptIndex,
        int recordingRevision,
        SpeakingTurnStatus status,
        double durationSeconds,
        String transcript,
        Double sttConfidence,
        String userAudioUrl,
        String assistantText,
        SpeakingPromptGuideResponseDto promptGuide,
        String assistantAudioUrl,
        List<AssistanceType> assistanceUsage,
        boolean excludedFromEvaluation,
        SpeakingStage failedStage,
        String errorCode,
        String errorMessage,
        int manualRetryCount,
        LocalDateTime completedAt
) {
}
