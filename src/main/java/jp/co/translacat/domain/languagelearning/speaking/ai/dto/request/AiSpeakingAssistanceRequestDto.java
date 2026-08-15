package jp.co.translacat.domain.languagelearning.speaking.ai.dto.request;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingConversationMessageDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;

import java.util.List;

public record AiSpeakingAssistanceRequestDto(
        String requestId,
        String idempotencyKey,
        String sessionId,
        int turnIndex,
        AssistanceType assistanceType,
        String originLanguage,
        String learningLanguage,
        String topic,
        String targetLevel,
        String assistantText,
        List<AiSpeakingConversationMessageDto> conversationHistory,
        List<SelectedKeywordDto> selectedKeywords,
        String sessionSummary
) {
}
