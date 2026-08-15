package jp.co.translacat.domain.languagelearning.speaking.session.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationStatus;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingSessionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SpeakingSessionResponseDto(
        Long id,
        LocalDate learningDate,
        Long topicId,
        String topicTitle,
        String topicCategory,
        Integer topicVersion,
        String customTopic,
        String goal,
        String persona,
        String originLanguage,
        String learningLanguage,
        SpeakingSessionStatus status,
        SpeakingEvaluationStatus evaluationStatus,
        ConversationStartMode conversationStartMode,
        ConversationStartMode resolvedStartMode,
        CorrectionMode correctionMode,
        int targetMinutes,
        int maxTurns,
        int completedTurns,
        long totalDurationSeconds,
        String voiceId,
        String playbackSpeed,
        String openingAssistantText,
        String openingAssistantAudioUrl,
        String sessionSummary,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime lastActivityAt
) {
}
