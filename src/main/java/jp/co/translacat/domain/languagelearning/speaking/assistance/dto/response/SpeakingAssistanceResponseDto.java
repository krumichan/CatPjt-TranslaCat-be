package jp.co.translacat.domain.languagelearning.speaking.assistance.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;

public record SpeakingAssistanceResponseDto(
        AssistanceType type,
        Long targetTurnId,
        int appliesToTurnIndex,
        String content,
        String audioUrl,
        double playbackRate
) {
}
