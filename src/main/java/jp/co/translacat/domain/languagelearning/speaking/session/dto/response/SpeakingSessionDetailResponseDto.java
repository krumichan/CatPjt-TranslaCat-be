package jp.co.translacat.domain.languagelearning.speaking.session.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnResponseDto;

import java.util.List;

public record SpeakingSessionDetailResponseDto(
        SpeakingSessionResponseDto session,
        SpeakingDailyUsageResponseDto dailyUsage,
        List<SpeakingTurnResponseDto> turns,
        boolean resumable
) {
}
