package jp.co.translacat.domain.languagelearning.daily.factory;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.dto.LanguageComplexityContext;
import jp.co.translacat.domain.languagelearning.quality.policy.LanguageComplexityPolicy;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationDiversityContextService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyWritingGenerationRequestFactory {

    private final GenerationDiversityContextService diversityContextService;
    private final LanguageComplexityPolicy complexityPolicy;

    public AiDailyWritingGenerationRequestDto createInitial(
            Long userId,
            DailyWritingSnapshot snapshot
    ) {
        return create(
                userId,
                "daily-generate-" + snapshot.snapshotId(),
                snapshot,
                snapshot.sentenceCount(),
                snapshot.difficultyDistribution()
        );
    }

    public AiDailyWritingGenerationRequestDto createRegeneration(
            Long userId,
            DailyWritingSet dailySet,
            DailyWritingSnapshot snapshot,
            int sentenceCount,
            DifficultyDistributionDto difficultyDistribution
    ) {
        String requestId = "daily-regen-"
                + dailySet.getId()
                + "-"
                + (dailySet.getRegenerationCount() + 1);

        return create(
                userId,
                requestId,
                snapshot,
                sentenceCount,
                difficultyDistribution
        );
    }

    private AiDailyWritingGenerationRequestDto create(
            Long userId,
            String requestId,
            DailyWritingSnapshot snapshot,
            int sentenceCount,
            DifficultyDistributionDto difficultyDistribution
    ) {
        return new AiDailyWritingGenerationRequestDto(
                requestId,
                snapshot.originLanguage(),
                snapshot.learningLanguage(),
                sentenceCount,
                difficultyDistribution,
                snapshot.selectedKeywords(),
                snapshot.learningProfile(),
                snapshot.recentEvaluationSummary(),
                snapshot.recentMistakes(),
                snapshot.recentlyLearnedExpressions(),
                snapshot.generationDate(),
                snapshot.snapshotId(),
                new LanguageComplexityContext(
                        snapshot.learningProfile() == null ? null : snapshot.learningProfile().baseLevelScore(),
                        complexityPolicy.baseBand(snapshot.learningProfile() == null ? null : snapshot.learningProfile().baseLevelScore()),
                        null,
                        LanguageComplexityPolicy.VERSION
                ),
                diversityContextService.context(
                        userId,
                        snapshot.learningLanguage(),
                        LanguageLearningContentSource.WRITING
                ),
                jp.co.translacat.domain.languagelearning.quality.service.GenerationFingerprintCommandService.POLICY_VERSION
        );
    }
}
