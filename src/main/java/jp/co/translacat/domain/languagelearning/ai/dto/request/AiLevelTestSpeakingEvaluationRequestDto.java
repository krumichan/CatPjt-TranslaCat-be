package jp.co.translacat.domain.languagelearning.ai.dto.request;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;

import java.util.List;

public record AiLevelTestSpeakingEvaluationRequestDto(
        String requestId,
        String idempotencyKey,
        Long sessionId,
        Long itemId,
        LevelTestItemType itemType,
        String promptText,
        String referenceText,
        String originLanguage,
        String learningLanguage,
        int complexityBand,
        int maxDurationSeconds,
        List<String> phraseHints,
        List<String> providedFacts,
        List<String> requiredIntents,
        List<String> responseConstraints,
        int manualRetryAttempt
) {
}
