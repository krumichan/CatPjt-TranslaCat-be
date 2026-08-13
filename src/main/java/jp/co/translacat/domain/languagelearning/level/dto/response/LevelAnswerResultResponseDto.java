package jp.co.translacat.domain.languagelearning.level.dto.response;

import jp.co.translacat.domain.languagelearning.daily.dto.response.WritingEvaluationResponseDto;

public record LevelAnswerResultResponseDto(
        Long sessionId,
        int questionNumber,
        WritingEvaluationResponseDto evaluation,
        boolean completed,
        Double baseLevelScore,
        LevelQuestionResponseDto nextQuestion
) {
}
