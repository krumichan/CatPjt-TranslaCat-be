package jp.co.translacat.domain.languagelearning.ai.dto.model;

import java.util.List;

public record RecentEvaluationSummaryDto(
        int sampleCount,
        Double overallAverage,
        WritingSkillScoresDto skillScores,
        List<String> recentFocus
) {
}
