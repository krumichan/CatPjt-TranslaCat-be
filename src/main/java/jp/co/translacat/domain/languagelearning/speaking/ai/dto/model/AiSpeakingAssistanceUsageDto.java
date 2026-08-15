package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;

public record AiSpeakingAssistanceUsageDto(
        AssistanceType type,
        int count
) {
}
