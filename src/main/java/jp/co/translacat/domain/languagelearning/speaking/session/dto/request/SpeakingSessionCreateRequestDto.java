package jp.co.translacat.domain.languagelearning.speaking.session.dto.request;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;

public record SpeakingSessionCreateRequestDto(
        Long topicId,
        boolean keywordBasedTopic,
        String customTopic,
        String goal,
        String persona,
        SpeakingPracticeMode practiceMode,
        ConversationStartMode conversationStartMode,
        CorrectionMode correctionMode,
        int targetMinutes,
        String voiceId,
        String playbackSpeed,
        String idempotencyKey
) {
}
