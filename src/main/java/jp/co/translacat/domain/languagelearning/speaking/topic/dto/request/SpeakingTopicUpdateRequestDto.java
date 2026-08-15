package jp.co.translacat.domain.languagelearning.speaking.topic.dto.request;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;

public record SpeakingTopicUpdateRequestDto(
        String title,
        String description,
        String recommendedLevel,
        ConversationStartMode recommendedStartMode,
        Integer sortOrder,
        Boolean active
) {
}
