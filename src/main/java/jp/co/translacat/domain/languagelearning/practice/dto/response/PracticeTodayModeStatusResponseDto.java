package jp.co.translacat.domain.languagelearning.practice.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;

public record PracticeTodayModeStatusResponseDto(
        String mode,
        Long practiceSetId,
        PracticeSetStatus status,
        int answeredCount,
        int questionCount,
        Double officialScore
) {
}
