package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.RecentLearningResponseDto;
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
import java.util.Set;
import java.util.stream.Collectors;

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

    public DashboardResponseDto get(Long userId) {
        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        LocalDate today = userSettingQueryService.resolveToday(setting);
        ProfileResponseDto profile = profileQueryService.getProfile(userId);
        List<WritingEvaluation> evaluations = getDailyEvaluations(userId);
        Optional<DailyWritingSet> todaySet = dailySetRepository
                .findByUserIdAndLearningDate(userId, today);

        return new DashboardResponseDto(
                countTodayCompleted(todaySet),
                todaySet.map(DailyWritingSet::getSentenceCount).orElse(0),
                currentStreak(userId, today),
                answerRepository.countDistinctAnsweredItems(userId),
                scoreCalculator.averageOverall(scoreCalculator.filter(
                        evaluations,
                        today.minusDays(6),
                        today
                )),
                scoreCalculator.averageOverall(scoreCalculator.filter(
                        evaluations,
                        today.withDayOfMonth(1),
                        today
                )),
                profile.skillScores(),
                scoreCalculator.scoreTrend(
                        evaluations,
                        today.minusDays(29),
                        today
                ),
                profile.difficultyPerformance(),
                profile.keywordMasteries(),
                profile.grammarWeaknesses(),
                profile.errorPatterns(),
                recentLearning(userId, evaluations),
                scoreCalculator.monthlyReport(
                        evaluations,
                        YearMonth.from(today)
                )
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

    private int countTodayCompleted(
            Optional<DailyWritingSet> todaySet
    ) {
        return todaySet.map(dailySet -> (int) itemRepository
                .findAllByDailySetIdOrderByOrderNoAsc(dailySet.getId())
                .stream()
                .filter(item -> answerRepository.existsByDailyItemId(
                        item.getId()
                ))
                .count()
        ).orElse(0);
    }

    private int currentStreak(Long userId, LocalDate today) {
        Set<LocalDate> completedDates = dailySetRepository
                .findAllByUserIdAndStatusOrderByLearningDateDesc(
                        userId,
                        DailySetStatus.COMPLETED
                )
                .stream()
                .map(DailyWritingSet::getLearningDate)
                .collect(Collectors.toSet());

        int streak = 0;
        LocalDate cursor = completedDates.contains(today)
                ? today
                : today.minusDays(1);

        while (completedDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }

        return streak;
    }

    private List<RecentLearningResponseDto> recentLearning(
            Long userId,
            List<WritingEvaluation> evaluations
    ) {
        return dailySetRepository.findTop30ByUserIdOrderByLearningDateDesc(
                userId
        ).stream()
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
}
