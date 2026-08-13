package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.ProfileSignalsDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.keyword.entity.KeywordMastery;
import jp.co.translacat.domain.languagelearning.keyword.repository.KeywordMasteryRepository;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfile;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LearningProfileCommandService {

    private static final double CALIBRATION_WEIGHT = 0.50;
    private static final double ACTIVE_WEIGHT = 0.30;

    private final LearningProfileQueryService queryService;
    private final LearningProfileSignalService signalService;
    private final KeywordMasteryRepository masteryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void prepareDailyLearning(
            Long userId,
            LocalDate learningDate
    ) {
        LearningProfile profile = queryService.getOrCreate(userId);

        if (profile.getState() == LearningProfileState.LEVEL_TEST_REQUIRED) {
            throw new BusinessException(
                    "최초 Level Test가 필요합니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_REQUIRED
            );
        }

        profile.startCalibrationIfNeeded(learningDate);
        profile.advanceCalibration(learningDate);
    }

    @Transactional
    public void completeLevelTest(Long userId, double baseLevelScore) {
        queryService.getOrCreate(userId)
                .completeLevelTest(baseLevelScore);
    }

    @Transactional
    public void resetForRecheck(Long userId) {
        queryService.getOrCreate(userId)
                .resetForRecheck();
    }

    @Transactional
    public void applyDailyEvaluation(
            Long userId,
            AiWritingEvaluationResponseDto response,
            DailyWritingDifficulty difficulty,
            List<SelectedKeywordDto> keywords,
            LocalDate learningDate
    ) {
        LearningProfile profile = queryService.getOrCreate(userId);
        profile.startCalibrationIfNeeded(learningDate);
        profile.advanceCalibration(learningDate);

        double weight = resolveWeight(profile);
        applySkillScores(profile, response, difficulty, weight);
        applyProfileSignals(userId, response.profileSignals());
        applyKeywordMastery(
                userId,
                response,
                keywords,
                weight
        );
    }

    private double resolveWeight(LearningProfile profile) {
        return profile.getState() == LearningProfileState.CALIBRATING
                ? CALIBRATION_WEIGHT
                : ACTIVE_WEIGHT;
    }

    private void applySkillScores(
            LearningProfile profile,
            AiWritingEvaluationResponseDto response,
            DailyWritingDifficulty difficulty,
            double weight
    ) {
        var scores = response.scores();

        profile.applyScores(
                scores.meaning(),
                scores.grammar(),
                scores.vocabulary(),
                scores.naturalness(),
                scores.expression(),
                weight,
                difficulty
        );
    }

    private void applyProfileSignals(
            Long userId,
            ProfileSignalsDto signals
    ) {
        if (signals == null) {
            return;
        }

        signalService.touchAll(
                userId,
                ProfileSignalType.STRENGTH,
                signals.strengthTags()
        );
        signalService.touchAll(
                userId,
                ProfileSignalType.WEAKNESS,
                signals.weaknessTags()
        );
        signalService.touchAll(
                userId,
                ProfileSignalType.GRAMMAR_WEAKNESS,
                signals.grammarPatterns()
        );
        signalService.touchAll(
                userId,
                ProfileSignalType.ERROR_PATTERN,
                merge(
                        signals.vocabularyPatterns(),
                        signals.naturalnessPatterns(),
                        signals.expressionPatterns(),
                        signals.meaningPatterns()
                )
        );
        signalService.touchAll(
                userId,
                ProfileSignalType.RECOMMENDED_FOCUS,
                signals.recommendedFocus()
        );
    }

    private void applyKeywordMastery(
            Long userId,
            AiWritingEvaluationResponseDto response,
            List<SelectedKeywordDto> keywords,
            double weight
    ) {
        double keywordScore = (
                response.scores().vocabulary()
                        + response.scores().meaning()
        ) / 2.0;
        User user = getUser(userId);

        for (SelectedKeywordDto keyword : safe(keywords)) {
            String canonicalKey = resolveCanonicalKey(keyword);
            KeywordMastery mastery = masteryRepository
                    .findByUserIdAndCanonicalKey(userId, canonicalKey)
                    .orElseGet(() -> masteryRepository.save(
                            KeywordMastery.create(user, canonicalKey)
                    ));

            mastery.applyScore(keywordScore, weight);
        }
    }

    private String resolveCanonicalKey(SelectedKeywordDto keyword) {
        if (keyword.canonicalKey() == null
                || keyword.canonicalKey().isBlank()) {
            return keyword.text().toLowerCase(Locale.ROOT);
        }
        return keyword.canonicalKey();
    }

    @SafeVarargs
    private final List<String> merge(List<String>... lists) {
        List<String> result = new ArrayList<>();
        for (List<String> list : lists) {
            result.addAll(safe(list));
        }
        return result;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));
    }
}
