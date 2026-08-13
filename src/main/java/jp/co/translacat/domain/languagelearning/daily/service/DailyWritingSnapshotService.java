package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DifficultyDistributionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.RecentEvaluationSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.keyword.facade.KeywordSelectionFacade;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileAiContextService;
import jp.co.translacat.domain.languagelearning.profile.service.RecentWritingEvaluationQueryService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DailyWritingSnapshotService {

    private final KeywordSelectionFacade keywordSelectionFacade;
    private final LearningProfileAiContextService profileAiContextService;
    private final RecentWritingEvaluationQueryService recentEvaluationQueryService;
    private final LanguageLearningJsonCodec jsonCodec;

    public DailyWritingSnapshot create(
            Long userId,
            LocalDate learningDate,
            LanguageLearningUserSetting userSetting,
            LanguageLearningAdminSetting adminSetting,
            int sentenceCount,
            DifficultyDistributionDto difficultyDistribution
    ) {
        List<SelectedKeywordDto> keywords = keywordSelectionFacade.selectForDailySet(
                userId,
                learningDate,
                adminSetting
        );
        LearningProfileSummaryDto profile =
                profileAiContextService.buildSummary(userId);
        RecentEvaluationSummaryDto recentEvaluation =
                recentEvaluationQueryService.getSummary(userId);

        List<String> recentMistakes = profile == null
                ? List.of()
                : safe(profile.grammarWeaknesses());
        List<String> recentlyLearnedExpressions = profile == null
                ? List.of()
                : safe(profile.recommendedFocus());

        return new DailyWritingSnapshot(
                userSetting.getOriginLanguage(),
                userSetting.getLearningLanguage(),
                sentenceCount,
                difficultyDistribution,
                keywords,
                profile,
                recentEvaluation,
                recentMistakes,
                recentlyLearnedExpressions,
                learningDate,
                UUID.randomUUID().toString()
        );
    }

    public DailyWritingSnapshot read(DailyWritingSet dailySet) {
        return jsonCodec.read(
                dailySet.getSnapshotJson(),
                DailyWritingSnapshot.class
        );
    }

    public String write(DailyWritingSnapshot snapshot) {
        return jsonCodec.write(snapshot);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
