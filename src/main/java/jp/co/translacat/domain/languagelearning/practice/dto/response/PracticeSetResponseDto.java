package jp.co.translacat.domain.languagelearning.practice.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus;

import java.time.LocalDate;
import java.util.List;

public record PracticeSetResponseDto(
        Long practiceSetId,
        LocalDate learningDate,
        PracticeDomain domain,
        String mode,
        PracticeSetStatus status,
        int questionCount,
        int answeredCount,
        int correctCount,
        Double officialScore,
        int complexityBand,
        String promptVersion,
        List<PracticeMetricResponseDto> metrics,
        List<PracticeQuestionResponseDto> questions,
        PracticeGenerationStatus generationStatus,
        int generatedQuestionCount,
        String generationFailureMessage
) {
}
