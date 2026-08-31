package jp.co.translacat.domain.languagelearning.ai.dto.request;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;

import java.util.List;

public record AiLevelTestTextEvaluationRequestDto(
        String requestId,
        String idempotencyKey,
        Long sessionId,
        Long itemId,
        LevelTestDomain domain,
        LevelTestItemType itemType,
        String promptText,
        String answer,
        String originLanguage,
        String learningLanguage,
        int complexityBand,
        String sourceText,
        List<String> referenceMeanings,
        List<String> keyMeaningUnits,
        String translationSourceText,
        List<String> providedFacts,
        List<String> requiredIntents,
        List<String> responseConstraints,
        List<String> focusMetrics,
        int manualRetryAttempt
) {
}
