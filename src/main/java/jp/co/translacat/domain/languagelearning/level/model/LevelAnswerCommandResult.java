package jp.co.translacat.domain.languagelearning.level.model;

import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;

public record LevelAnswerCommandResult(
        Long sessionId,
        int questionNumber,
        WritingEvaluation evaluation,
        boolean completed,
        Double baseLevelScore
) {
}
