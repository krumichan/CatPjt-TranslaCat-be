package jp.co.translacat.domain.languagelearning.ai.dto.model;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;

import java.util.List;

public record VocabularyPlanItemDto(
        int globalOrder,
        boolean reviewTarget,
        String targetExpression,
        String canonicalKey,
        List<String> distractors,
        String skillTag,
        PracticeDifficulty difficulty,
        int complexityBand,
        String scenarioFamily,
        String anchorType,
        String anchorValue
) {
}
