package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import java.util.List;

public record AiSpeakingConversationResultDto(
        String intent,
        String difficulty,
        boolean shouldEnd,
        String endReason,
        String hint,
        List<AiSpeakingCoachingCorrectionDto> coachingCorrections,
        String sessionSummary,
        String assistanceLevel
) {
}
