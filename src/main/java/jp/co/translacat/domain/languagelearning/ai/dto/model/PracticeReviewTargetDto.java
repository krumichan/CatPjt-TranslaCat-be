package jp.co.translacat.domain.languagelearning.ai.dto.model;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;

import java.util.List;

public record PracticeReviewTargetDto(
        String canonicalKey,
        String expression,
        Double masteryScore,
        int wrongCount,
        List<PracticeQuestionType> previousQuestionTypes
) {
}
