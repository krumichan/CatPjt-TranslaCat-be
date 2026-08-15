package jp.co.translacat.domain.languagelearning.speaking.ai.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingAssistantTurnDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingConversationResultDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingUsageDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;

public record AiSpeakingSessionStartResponseDto(
        String requestId,
        String sessionId,
        ConversationStartMode resolvedStartMode,
        AiSpeakingAssistantTurnDto assistant,
        AiSpeakingConversationResultDto conversation,
        AiSpeakingUsageDto usage
) {
}
