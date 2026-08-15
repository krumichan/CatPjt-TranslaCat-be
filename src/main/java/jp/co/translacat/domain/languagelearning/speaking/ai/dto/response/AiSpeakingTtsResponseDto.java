package jp.co.translacat.domain.languagelearning.speaking.ai.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingAssistantAudioDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingUsageDto;

public record AiSpeakingTtsResponseDto(
        String requestId,
        String sessionId,
        AiSpeakingAssistantAudioDto audio,
        AiSpeakingUsageDto usage
) {
}
