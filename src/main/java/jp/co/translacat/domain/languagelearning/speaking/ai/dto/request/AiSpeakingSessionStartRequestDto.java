package jp.co.translacat.domain.languagelearning.speaking.ai.dto.request;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingAssistanceUsageDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingConversationMessageDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingSessionPolicySnapshotDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;

import java.util.List;

public record AiSpeakingSessionStartRequestDto(
        String requestId,
        String idempotencyKey,
        String sessionId,
        int turnIndex,
        String originLanguage,
        String learningLanguage,
        String topic,
        String category,
        String goal,
        String persona,
        ConversationStartMode conversationStartMode,
        ConversationStartMode topicRecommendedStartMode,
        CorrectionMode correctionMode,
        String targetLevel,
        LearningProfileSummaryDto learningProfileSummary,
        List<SelectedKeywordDto> selectedKeywords,
        List<String> focusSignals,
        List<AiSpeakingConversationMessageDto> conversationHistory,
        List<AiSpeakingAssistanceUsageDto> assistanceUsage,
        String sessionSummary,
        double sessionElapsedSeconds,
        AiSpeakingSessionPolicySnapshotDto sessionPolicySnapshot,
        String audioReference,
        String audioFormat,
        Double durationSeconds,
        String voice,
        String playbackSpeed,
        int manualRetryAttempt
) {
}
