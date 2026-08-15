package jp.co.translacat.domain.languagelearning.speaking.topic.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTopicCategory;

public record SpeakingTopicResponseDto(
        Long id,
        String topicCode,
        SpeakingTopicCategory category,
        String title,
        String description,
        String originLanguage,
        String learningLanguage,
        String recommendedLevel,
        ConversationStartMode recommendedStartMode,
        int sortOrder,
        int version
) {
}
