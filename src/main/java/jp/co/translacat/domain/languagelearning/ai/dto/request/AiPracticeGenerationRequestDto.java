package jp.co.translacat.domain.languagelearning.ai.dto.request;

import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeReviewTargetDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;

import java.time.LocalDate;
import java.util.List;

public record AiPracticeGenerationRequestDto(
        String requestId,
        PracticeDomain domain,
        String mode,
        String originLanguage,
        String learningLanguage,
        int questionCount,
        int complexityBand,
        int easierCount,
        int currentCount,
        int challengeCount,
        List<String> selectedKeywords,
        List<String> weakSignals,
        List<String> recentMistakes,
        List<PracticeReviewTargetDto> reviewTargets,
        int reviewQuestionCount,
        LocalDate generationDate
) {
}
