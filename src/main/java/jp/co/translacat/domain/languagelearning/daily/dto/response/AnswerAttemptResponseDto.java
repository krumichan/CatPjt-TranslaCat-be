package jp.co.translacat.domain.languagelearning.daily.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AnswerAttemptResponseDto(
        Long answerId,
        LocalDate attemptDate,
        String answer,
        LocalDateTime submittedAt,
        WritingEvaluationResponseDto evaluation
) {
}
