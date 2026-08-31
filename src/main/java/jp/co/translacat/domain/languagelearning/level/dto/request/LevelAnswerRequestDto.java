package jp.co.translacat.domain.languagelearning.level.dto.request;

import java.util.List;

public record LevelAnswerRequestDto(
        String selectedOptionKey,
        List<String> selectedOptionKeys,
        String textAnswer,
        String idempotencyKey
) {
}
