package jp.co.translacat.domain.languagelearning.daily.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;

import java.time.LocalDate;

public record AnswerResultResponseDto(
        Long answerId,
        Long itemId,
        LocalDate attemptDate,
        EvaluationStatus evaluationStatus,
        String evaluationFailureMessage,
        WritingEvaluationResponseDto evaluation
) {
}
