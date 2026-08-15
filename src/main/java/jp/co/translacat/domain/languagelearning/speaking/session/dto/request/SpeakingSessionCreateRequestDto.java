package jp.co.translacat.domain.languagelearning.speaking.session.dto.request;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;

public record SpeakingSessionCreateRequestDto(
        Long topicId,
        String customTopic,
        String goal,
        String persona,
        ConversationStartMode conversationStartMode,
        CorrectionMode correctionMode,
        int targetMinutes,
        String voiceId,
        String playbackSpeed,
        String idempotencyKey
) {
}
