package jp.co.translacat.domain.languagelearning.speaking.ai.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingUsageDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;

public record AiSpeakingAssistanceResponseDto(
        String requestId,
        String sessionId,
        int turnIndex,
        AssistanceType type,
        String content,
        AiSpeakingUsageDto usage,
        boolean idempotentReplay
) {
}
