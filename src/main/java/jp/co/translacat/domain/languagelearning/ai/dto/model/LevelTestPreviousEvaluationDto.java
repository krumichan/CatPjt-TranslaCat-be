package jp.co.translacat.domain.languagelearning.ai.dto.model;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDifficulty;

public record LevelTestPreviousEvaluationDto(
        int questionNumber,
        LevelTestDifficulty difficulty,
        WritingEvaluationScoresDto scores
) {
}
