package jp.co.translacat.domain.languagelearning.practice.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus;

public record PracticeTodayModeStatusResponseDto(
        String mode,
        Long practiceSetId,
        PracticeSetStatus status,
        int answeredCount,
        int questionCount,
        Double officialScore,
        PracticeGenerationStatus generationStatus,
        int generatedQuestionCount,
        String generationFailureMessage
) {
}
