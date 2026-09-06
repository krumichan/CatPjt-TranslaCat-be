package jp.co.translacat.domain.languagelearning.daily.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AnswerAttemptResponseDto(
        Long answerId,
        LocalDate attemptDate,
        String answer,
        LocalDateTime submittedAt,
        EvaluationStatus evaluationStatus,
        String evaluationFailureMessage,
        WritingEvaluationResponseDto evaluation
) {
}
