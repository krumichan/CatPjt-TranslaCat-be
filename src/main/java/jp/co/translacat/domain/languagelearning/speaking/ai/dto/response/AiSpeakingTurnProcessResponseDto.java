package jp.co.translacat.domain.languagelearning.speaking.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingAssistantTurnDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingConversationResultDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingErrorDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingTranscriptDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingUsageDto;

import java.util.Map;

public record AiSpeakingTurnProcessResponseDto(
        String requestId,
        String sessionId,
        int turnIndex,
        String status,
        AiSpeakingTranscriptDto transcript,
        AiSpeakingAssistantTurnDto assistant,
        AiSpeakingConversationResultDto conversation,
        String failedStage,
        AiSpeakingErrorDto error,
        AiSpeakingUsageDto usage,
        @JsonProperty("idempotentReplay") boolean idempotentReplay,
        Map<String, Object> internalMetadata
) {
}
