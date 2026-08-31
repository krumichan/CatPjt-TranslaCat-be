package jp.co.translacat.domain.languagelearning.ai.dto.request;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.RecentEvaluationSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.dto.LanguageComplexityContext;

import java.time.LocalDate;
import java.util.List;

public record AiDailyWritingGenerationRequestDto(
        String requestId,
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
        String snapshotId,
        LanguageComplexityContext languageComplexity,
        DiversityContext diversityContext,
        String contentDiversityPolicyVersion
) {

    public AiDailyWritingGenerationRequestDto(
            String requestId,
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
        this(
                requestId,
                originLanguage,
                learningLanguage,
                sentenceCount,
                difficultyDistribution,
                selectedKeywords,
                learningProfile,
                recentEvaluationSummary,
                recentMistakes,
                recentlyLearnedExpressions,
                generationDate,
                snapshotId,
                null,
                DiversityContext.empty(),
                null
        );
    }
}
