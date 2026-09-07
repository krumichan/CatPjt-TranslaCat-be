package jp.co.translacat.domain.languagelearning.practice.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;

import java.util.List;

public record PracticeQuestionResponseDto(
        Long questionId,
        int order,
        PracticeQuestionType questionType,
        PracticeDifficulty difficulty,
        int complexityBand,
        String passageId,
        String passageText,
        String prompt,
        List<PracticeOptionResponseDto> options,
        String skillTag,
        String targetExpression,
        boolean reviewTarget,
        List<String> vocabularyCandidates,
        boolean answered,
        boolean correct,
        boolean canRetry,
        List<PracticeAttemptResponseDto> attempts,
        List<String> correctAnswer,
        String evidenceText,
        String explanationOrigin,
        String explanationLearning
) {
}
