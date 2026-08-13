package jp.co.translacat.domain.languagelearning.daily.model;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.RecentEvaluationSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;

import java.time.LocalDate;
import java.util.List;

public record DailyWritingSnapshot(
        String originLanguage,
        String learningLanguage,
        int sentenceCount,
        DifficultyDistributionDto difficultyDistribution,
        List<SelectedKeywordDto> selectedKeywords,
        LearningProfileSummaryDto learningProfile,
        RecentEvaluationSummaryDto recentEvaluationSummary,
        List<String> recentMistakes,
        List<String> recentlyLearnedExpressions,
        LocalDate generationDate,
        String snapshotId
) {
}
