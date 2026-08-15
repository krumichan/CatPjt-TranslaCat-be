package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.RecentLearningResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.StreakResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.ProfileResponseDto;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileQueryService;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LanguageLearningDashboardQueryService {

    private final DailyWritingSetRepository dailySetRepository;
    private final DailyWritingItemRepository itemRepository;
    private final WritingAnswerRepository answerRepository;
    private final WritingEvaluationRepository evaluationRepository;
    private final LearningProfileQueryService profileQueryService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final DashboardScoreCalculator scoreCalculator;
    private final SpeakingDashboardQueryService speakingDashboardQueryService;
    private final LearningStreakQueryService streakQueryService;
    private final SourceSkillTrendQueryService sourceSkillTrendQueryService;
    private final DashboardInsightQueryService insightQueryService;

    public DashboardResponseDto get(Long userId) {
        return get(userId, "7d", "ALL");
    }

    public DashboardResponseDto get(
            Long userId,
            String period,
            String sourceValue
    ) {
        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        LocalDate today = userSettingQueryService.resolveToday(setting);
        ProfileResponseDto profile = profileQueryService.getProfile(userId);
        List<WritingEvaluation> writingEvaluations = getDailyEvaluations(userId);
        Optional<DailyWritingSet> todaySet = dailySetRepository
                .findByUserIdAndLearningDate(userId, today);
        LocalDate from = today.minusDays(resolveDays(period) - 1L);
        LearningSource source = parseSource(sourceValue);
        StreakResponseDto streak = streakQueryService.get(userId, today);

        return new DashboardResponseDto(
                countTodayCompleted(todaySet),
                todaySet.map(DailyWritingSet::getSentenceCount).orElse(0),
                streak.current(),
                answerRepository.countDistinctAnsweredItems(userId),
                scoreCalculator.averageOverall(scoreCalculator.filter(
                        writingEvaluations,
                        today.minusDays(6),
                        today
                )),
                scoreCalculator.averageOverall(scoreCalculator.filter(
                        writingEvaluations,
                        today.withDayOfMonth(1),
                        today
                )),
                profile.skillScores(),
                scoreCalculator.scoreTrend(
                        writingEvaluations,
                        today.minusDays(29),
                        today
                ),
                profile.difficultyPerformance(),
                profile.keywordMasteries(),
                profile.grammarWeaknesses(),
                profile.errorPatterns(),
                recentLearning(userId, writingEvaluations),
                scoreCalculator.monthlyReport(
                        writingEvaluations,
                        YearMonth.from(today)
                ),
                speakingDashboardQueryService.getToday(
                        userId,
                        today,
                        setting.getDailySpeakingGoalMinutes()
                ),
                streak,
                speakingDashboardQueryService.getSummary(userId, from, today),
                sourceSkillTrendQueryService.get(userId, source, from, today),
                insightQueryService.get(userId, source),
                source == null ? "ALL" : source.name()
        );
    }

    private List<WritingEvaluation> getDailyEvaluations(Long userId) {
        return evaluationRepository
                .findAllByUserIdAndContextAndStatusOrderByEvaluatedAtDesc(
                        userId,
                        WritingEvaluationContext.DAILY,
                        EvaluationStatus.SUCCESS
                );
    }

    private int countTodayCompleted(Optional<DailyWritingSet> todaySet) {
        return todaySet.map(dailySet -> (int) itemRepository
                .findAllByDailySetIdOrderByOrderNoAsc(dailySet.getId())
                .stream()
                .filter(item -> answerRepository.existsByDailyItemId(item.getId()))
                .count()
        ).orElse(0);
    }

    private List<RecentLearningResponseDto> recentLearning(
            Long userId,
            List<WritingEvaluation> evaluations
    ) {
        return dailySetRepository.findTop30ByUserIdOrderByLearningDateDesc(userId)
                .stream()
                .limit(10)
                .map(dailySet -> new RecentLearningResponseDto(
                        dailySet.getLearningDate(),
                        dailySet.getSentenceCount(),
                        dailySet.getStatus().name(),
                        scoreCalculator.averageOverall(
                                scoreCalculator.filterByLearningDate(
                                        evaluations,
                                        dailySet.getLearningDate()
                                )
                        )
                ))
                .toList();
    }

    private int resolveDays(String period) {
        if (period == null || !period.toLowerCase().endsWith("d")) {
            return 7;
        }
        try {
            return Math.max(
                    1,
                    Math.min(365, Integer.parseInt(
                            period.substring(0, period.length() - 1)
                    ))
            );
        } catch (NumberFormatException e) {
            return 7;
        }
    }

    private LearningSource parseSource(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return LearningSource.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
