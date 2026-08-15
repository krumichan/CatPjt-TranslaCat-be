package jp.co.translacat.domain.languagelearning.speaking.ai.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingConversationResultDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingUsageDto;

public record AiSpeakingConversationResponseDto(
        String requestId,
        String sessionId,
        int turnIndex,
        String assistantText,
        AiSpeakingConversationResultDto conversation,
        AiSpeakingUsageDto usage
) {
}
