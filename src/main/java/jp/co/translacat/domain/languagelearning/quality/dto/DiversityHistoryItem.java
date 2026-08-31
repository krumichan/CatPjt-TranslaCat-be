package jp.co.translacat.domain.languagelearning.quality.dto;

import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;

import java.util.List;

public record DiversityHistoryItem(
        LanguageLearningContentSource sourceType,
        String content,
        String contentHash,
        String scenarioCategory,
        String communicativeIntent,
        String taskArchetype,
        List<String> grammarFocusCodes,
        String semanticSummary,
        Integer ageDays
) {
}
