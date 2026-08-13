package jp.co.translacat.domain.languagelearning.daily.factory;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;

import org.springframework.stereotype.Component;

@Component
public class DailyWritingGenerationRequestFactory {

    public AiDailyWritingGenerationRequestDto createInitial(
            DailyWritingSnapshot snapshot
    ) {
        return create(
                "daily-generate-" + snapshot.snapshotId(),
                snapshot,
                snapshot.sentenceCount(),
                snapshot.difficultyDistribution()
        );
    }

    public AiDailyWritingGenerationRequestDto createRegeneration(
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
                requestId,
                snapshot,
                sentenceCount,
                difficultyDistribution
        );
    }

    private AiDailyWritingGenerationRequestDto create(
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
                snapshot.snapshotId()
        );
    }
}
