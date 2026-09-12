package jp.co.translacat.domain.languagelearning.daily.factory;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.dto.LanguageComplexityContext;
import jp.co.translacat.domain.languagelearning.quality.policy.LanguageComplexityPolicy;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationDiversityContextService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DailyWritingGenerationRequestFactory {

    private final GenerationDiversityContextService diversityContextService;
    private final LanguageComplexityPolicy complexityPolicy;
    private final DailyWritingItemRepository itemRepository;

    public AiDailyWritingGenerationRequestDto createItem(
            DailyWritingSet dailySet,
            DailyWritingSnapshot snapshot,
            int order,
            String generationToken
    ) {
        return create(
                dailySet.getUser().getId(),
                "daily-generate-" + dailySet.getId() + "-" + order + "-" + generationToken,
                dailySet,
                snapshot,
                1,
                distributionForItem(snapshot, order)
        );
    }

    public DifficultyDistributionDto distributionForItem(DailyWritingSnapshot snapshot, int order) {
        DifficultyDistributionDto distribution = snapshot.difficultyDistribution();
        if (order < 1 || order > snapshot.sentenceCount()
                || distribution.review() + distribution.normal() + distribution.challenge()
                        != snapshot.sentenceCount()) {
            throw new IllegalArgumentException("Invalid writing generation slot or difficulty distribution");
        }
        if (order <= distribution.review()) {
            return new DifficultyDistributionDto(1, 0, 0);
        }
        if (order <= distribution.review() + distribution.normal()) {
            return new DifficultyDistributionDto(0, 1, 0);
        }
        return new DifficultyDistributionDto(0, 0, 1);
    }

    public AiDailyWritingGenerationRequestDto createInitial(
            Long userId,
            DailyWritingSet dailySet,
            DailyWritingSnapshot snapshot
    ) {
        return create(
                userId,
                "daily-generate-" + snapshot.snapshotId(),
                dailySet,
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
            DifficultyDistributionDto difficultyDistribution,
            String regenerationToken
    ) {
        String requestId = "daily-regen-"
                + dailySet.getId()
                + "-"
                + (dailySet.getRegenerationCount() + 1)
                + "-"
                + regenerationToken;

        return create(
                userId,
                requestId,
                dailySet,
                snapshot,
                sentenceCount,
                difficultyDistribution
        );
    }

    private AiDailyWritingGenerationRequestDto create(
            Long userId,
            String requestId,
            DailyWritingSet dailySet,
            DailyWritingSnapshot snapshot,
            int sentenceCount,
            DifficultyDistributionDto difficultyDistribution
    ) {
        Map<String, String> currentContents = new LinkedHashMap<>();
        itemRepository.findAllByDailySetIdOrderByOrderNoAsc(dailySet.getId()).forEach(item ->
                currentContents.put(String.valueOf(item.getId()), item.getOriginText()));
        return new AiDailyWritingGenerationRequestDto(
                requestId,
                snapshot.originLanguage(),
                snapshot.learningLanguage(),
                dailySet.getWritingType(),
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
                diversityContextService.contextForSources(
                        userId,
                        snapshot.learningLanguage(),
                        LanguageLearningContentSource.WRITING,
                        currentContents
                ),
                jp.co.translacat.domain.languagelearning.quality.service.GenerationFingerprintCommandService.POLICY_VERSION
        );
    }
}
