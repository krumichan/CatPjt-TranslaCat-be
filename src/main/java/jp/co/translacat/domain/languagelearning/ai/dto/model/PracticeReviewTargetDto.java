package jp.co.translacat.domain.languagelearning.ai.dto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;

import java.util.List;

public record PracticeReviewTargetDto(
        String canonicalKey,
        String expression,
        Double masteryScore,
        int wrongCount,
        List<PracticeQuestionType> previousQuestionTypes,
        @JsonInclude(JsonInclude.Include.NON_NULL) String preferredSkill
) {
    public PracticeReviewTargetDto(
            String canonicalKey,
            String expression,
            Double masteryScore,
            int wrongCount,
            List<PracticeQuestionType> previousQuestionTypes
    ) {
        this(canonicalKey, expression, masteryScore, wrongCount, previousQuestionTypes, null);
    }
}
