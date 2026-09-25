package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.ProfileSignalsDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthOperation;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthCommands;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BE는 평가 사실만 전달한다. 점수 blend, 상태 전이, signal/mastery 갱신은 LL이 수행한다.
 */
@Service
@RequiredArgsConstructor
public class LearningProfileCommandService {
    private final GrowthCommands commands;
    private final GrowthReadGateway growth;

    @Transactional
    public void prepareDailyLearning(Long userId, LocalDate date) {
        var profile = growth.snapshot(userId).profile();
        if (profile == null || profile.state() == LearningProfileState.LEVEL_TEST_REQUIRED)
            throw new BusinessException("최초 Level Test가 필요합니다.", LanguageLearningErrorCode.LEVEL_TEST_REQUIRED);
        commands.append(userId,
                new GrowthOperation("PREPARE:" + date, "LEARNING_PREPARED", Map.of("learningDate", date.toString())));
    }

    @Transactional
    public void applyDailyEvaluation(Long userId, long evaluationId, AiWritingEvaluationResponseDto response,
                                     DailyWritingDifficulty difficulty, List<SelectedKeywordDto> keywords,
                                     LocalDate date) {
        var scores = response.scores();
        var payload = Map.<String, Object>of("learningDate", date.toString(), "difficulty", difficulty.name(), "scores",
                List.of(scores.meaning(), scores.grammar(), scores.vocabulary(), scores.naturalness(),
                        scores.expression()), "signals", signals(response.profileSignals()), "canonicalKeys",
                safe(keywords).stream()
                        .map(keyword -> keyword.canonicalKey() == null || keyword.canonicalKey().isBlank() ?
                                keyword.text().toLowerCase(Locale.ROOT) : keyword.canonicalKey())
                        .toList());
        commands.append(userId, new GrowthOperation("WRITING_EVALUATION:" + evaluationId, "WRITING_SCORED", payload));
    }

    private Map<String, List<String>> signals(ProfileSignalsDto value) {
        if (value == null) return Map.of();
        return Map.of("STRENGTH", texts(value.strengthTags()), "WEAKNESS", texts(value.weaknessTags()),
                "GRAMMAR_WEAKNESS", texts(value.grammarPatterns()), "ERROR_PATTERN",
                merge(value.vocabularyPatterns(), value.naturalnessPatterns(), value.expressionPatterns(),
                        value.meaningPatterns()), "RECOMMENDED_FOCUS", texts(value.recommendedFocus()));
    }

    @SafeVarargs
    private final List<String> merge(List<String>... lists) {
        List<String> result = new ArrayList<>();
        for (var values : lists) result.addAll(texts(values));
        return result;
    }

    private List<String> texts(List<String> values) {
        return safe(values).stream().filter(v -> v != null && !v.isBlank()).toList();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
