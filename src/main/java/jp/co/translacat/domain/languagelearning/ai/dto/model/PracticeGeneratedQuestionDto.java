package jp.co.translacat.domain.languagelearning.ai.dto.model;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;

import java.util.List;

public record PracticeGeneratedQuestionDto(
        int order,
        PracticeQuestionType questionType,
        PracticeDifficulty difficulty,
        int complexityBand,
        String passageId,
        String passageText,
        String prompt,
        List<PracticeOptionDto> options,
        List<String> correctAnswer,
        String skillTag,
        String evidenceText,
        String explanationOrigin,
        String explanationLearning,
        String targetExpression,
        String canonicalKey,
        boolean reviewTarget,
        List<String> vocabularyCandidates
) {
}
