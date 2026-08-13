package jp.co.translacat.domain.languagelearning.ai.dto.request;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestPreviousEvaluationDto;

import java.util.List;

public record AiLevelTestQuestionRequestDto(
        String requestId,
        String originLanguage,
        String learningLanguage,
        int questionNumber,
        int totalQuestions,
        List<LevelTestPreviousEvaluationDto> previousEvaluations
) {
}
