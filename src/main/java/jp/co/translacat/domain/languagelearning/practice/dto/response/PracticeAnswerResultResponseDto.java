package jp.co.translacat.domain.languagelearning.practice.dto.response;

import java.util.List;

public record PracticeAnswerResultResponseDto(
        Long questionId,
        int attemptNo,
        boolean correct,
        boolean official,
        boolean setCompleted,
        Double officialScore,
        List<String> correctAnswer,
        String evidenceText,
        String explanationOrigin,
        String explanationLearning
) {
}
