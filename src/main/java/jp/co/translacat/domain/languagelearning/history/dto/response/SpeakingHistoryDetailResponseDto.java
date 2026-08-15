package jp.co.translacat.domain.languagelearning.history.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.dto.response.SpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingSessionResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnResponseDto;

import java.util.List;

public record SpeakingHistoryDetailResponseDto(
        SpeakingSessionResponseDto session,
        List<SpeakingTurnResponseDto> turns,
        SpeakingEvaluationResponseDto evaluation
) {
}
