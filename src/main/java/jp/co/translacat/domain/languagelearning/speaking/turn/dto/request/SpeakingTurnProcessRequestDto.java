package jp.co.translacat.domain.languagelearning.speaking.turn.dto.request;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;

import java.util.List;

public record SpeakingTurnProcessRequestDto(
        Long turnId,
        String uploadToken,
        Double durationSeconds,
        List<AssistanceType> assistanceUsage
) {
}
