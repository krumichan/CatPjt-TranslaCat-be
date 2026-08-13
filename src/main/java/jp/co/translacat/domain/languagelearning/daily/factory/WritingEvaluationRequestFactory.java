package jp.co.translacat.domain.languagelearning.daily.factory;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiWritingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.model.WritingEvaluationRequestContext;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileAiContextService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class WritingEvaluationRequestFactory {

    private final LanguageLearningJsonCodec jsonCodec;
    private final LearningProfileAiContextService profileAiContextService;

    public WritingEvaluationRequestContext createDaily(
            WritingAnswer answer,
            LanguageLearningUserSetting setting,
            DailyWritingSnapshot snapshot,
            LocalDate learningDate
    ) {
        DailyWritingItem item = answer.getDailyItem();
        List<WritingMetric> focusMetrics = readFocusMetrics(item);
        List<SelectedKeywordDto> relevantKeywords = resolveRelevantKeywords(
                snapshot.selectedKeywords(),
                readUsedKeywords(item)
        );

        AiWritingEvaluationRequestDto request =
                new AiWritingEvaluationRequestDto(
                        "daily-eval-" + answer.getId() + "-" + learningDate,
                        WritingEvaluationContext.DAILY,
                        setting.getOriginLanguage(),
                        setting.getLearningLanguage(),
                        item.getOriginText(),
                        answer.getAnswerText(),
                        item.getDifficulty().name(),
                        relevantKeywords,
                        focusMetrics,
                        snapshot.learningProfile()
                );

        return new WritingEvaluationRequestContext(
                request,
                relevantKeywords
        );
    }

    public AiWritingEvaluationRequestDto createLevel(
            Long userId,
            LevelTestItem item,
            LanguageLearningUserSetting setting
    ) {
        return new AiWritingEvaluationRequestDto(
                "level-eval-"
                        + item.getSession().getId()
                        + "-"
                        + item.getQuestionNumber(),
                WritingEvaluationContext.LEVEL_TEST,
                setting.getOriginLanguage(),
                setting.getLearningLanguage(),
                item.getOriginText(),
                item.getAnswerText(),
                item.getDifficulty().name(),
                List.of(),
                jsonCodec.read(
                        item.getFocusMetricsJson(),
                        new TypeReference<>() {
                        }
                ),
                profileAiContextService.buildSummary(userId)
        );
    }

    private List<WritingMetric> readFocusMetrics(DailyWritingItem item) {
        return jsonCodec.read(
                item.getFocusMetricsJson(),
                new TypeReference<>() {
                }
        );
    }

    private List<String> readUsedKeywords(DailyWritingItem item) {
        return jsonCodec.read(
                item.getKeywordsJson(),
                new TypeReference<>() {
                }
        );
    }

    private List<SelectedKeywordDto> resolveRelevantKeywords(
            List<SelectedKeywordDto> selectedKeywords,
            List<String> usedKeywords
    ) {
        if (selectedKeywords == null) {
            return List.of();
        }
        if (usedKeywords == null || usedKeywords.isEmpty()) {
            return selectedKeywords;
        }

        Set<String> normalizedUsedKeywords = normalize(usedKeywords);
        List<SelectedKeywordDto> filtered = selectedKeywords.stream()
                .filter(keyword -> matches(
                        keyword,
                        normalizedUsedKeywords
                ))
                .toList();

        return filtered.isEmpty() ? selectedKeywords : filtered;
    }

    private Set<String> normalize(List<String> values) {
        Set<String> result = new HashSet<>();

        for (String value : values) {
            if (value == null) {
                continue;
            }

            result.add(value.toLowerCase(Locale.ROOT));
        }

        return result;
    }

    private boolean matches(
            SelectedKeywordDto keyword,
            Set<String> usedKeywords
    ) {
        if (usedKeywords.contains(keyword.text().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (usedKeywords.contains(keyword.key().toLowerCase(Locale.ROOT))) {
            return true;
        }

        return keyword.canonicalKey() != null
                && usedKeywords.contains(
                        keyword.canonicalKey().toLowerCase(Locale.ROOT)
                );
    }
}
