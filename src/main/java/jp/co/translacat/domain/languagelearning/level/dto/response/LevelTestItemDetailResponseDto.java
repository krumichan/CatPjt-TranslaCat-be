package jp.co.translacat.domain.languagelearning.level.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;

import java.util.List;
import java.util.Map;

public record LevelTestItemDetailResponseDto(
        Long itemId,
        int questionNumber,
        LevelTestDomain domain,
        LevelTestItemType itemType,
        int complexityBand,
        String instruction,
        String promptText,
        List<LevelTestOptionResponseDto> options,
        String emphasisText,
        LevelTestTaskGuidanceResponseDto taskGuidance,
        String selectedOptionKey,
        List<String> selectedOptionKeys,
        String textAnswer,
        boolean audioSubmitted,
        boolean answerAudioAvailable,
        boolean referenceAudioAvailable,
        String transcript,
        List<String> recommendedAnswers,
        List<Map<String, Object>> detailedFeedback,
        boolean modelAnswerAudioAvailable,
        String correctOptionKey,
        List<String> correctOrder,
        boolean evaluable,
        Integer score,
        Double confidence,
        List<Map<String, Object>> metrics,
        List<String> strengths,
        List<String> improvements,
        String reasonCode
) {
}
