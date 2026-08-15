package jp.co.translacat.domain.languagelearning.speaking.assistance.dto.request;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;

public record SpeakingAssistanceRequestDto(
        AssistanceType type,
        Long targetTurnId
) {
}
