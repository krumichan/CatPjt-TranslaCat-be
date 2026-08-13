package jp.co.translacat.domain.languagelearning.daily.dto.response;

import java.time.LocalDate;

public record AnswerResultResponseDto(
        Long answerId,
        Long itemId,
        LocalDate attemptDate,
        WritingEvaluationResponseDto evaluation
) {
}
