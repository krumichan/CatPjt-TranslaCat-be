package jp.co.translacat.domain.languagelearning.ai.dto.model;

import java.util.List;
import java.util.Map;

public record LearningProfileSummaryDto(
        String profileVersion,
        Double baseLevelScore,
        WritingSkillScoresDto skillScores,
        List<String> grammarWeaknesses,
        List<KeywordMasteryDto> keywordMasteries,
        DifficultyPerformanceDto difficultyPerformance,
        List<String> errorPatterns,
        String trend,
        Double confidence,
        List<String> strengths,
        List<String> weaknesses,
        List<String> recommendedFocus,
        Map<String, Object> additionalSignals
) {
}
