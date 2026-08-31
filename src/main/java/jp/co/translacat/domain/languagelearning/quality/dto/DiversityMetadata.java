package jp.co.translacat.domain.languagelearning.quality.dto;

import java.util.List;

public record DiversityMetadata(
        String scenarioCategory,
        String communicativeIntent,
        String taskArchetype,
        List<String> grammarFocusCodes,
        List<String> lexicalFocusCodes,
        String semanticSummary,
        Boolean requiresBackgroundKnowledge,
        String contentHash,
        String similarityKey
) {
}
