package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardInsightsResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardV3ResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.SourceSkillTrendResponseDto;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningWeaknessState;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningDailySetRepository;
import jp.co.translacat.domain.languagelearning.listening.dashboard.facade.ListeningDashboardFacade;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LanguageLearningDashboardV3QueryService {

    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final LanguageLearningDashboardQueryService legacyDashboardQueryService;
    private final SourceSkillTrendQueryService sourceSkillTrendQueryService;
    private final DashboardInsightQueryService insightQueryService;
    private final DashboardV3ProjectionPolicy projectionPolicy;
    private final ListeningDashboardFacade listeningDashboardFacade;
    private final ListeningDailySetRepository listeningDailySetRepository;

    public DashboardV3ResponseDto get(
            Long userId,
            LocalDate from,
            LocalDate to,
            String sourceValue,
            ListeningTaskType taskType
    ) {
        var setting = userSettingQueryService.getOrCreateEntity(userId);
        userSettingQueryService.requireConfigured(setting);
        LocalDate today = userSettingQueryService.resolveToday(setting);
        LocalDate resolvedTo = to == null ? today : to;
        LocalDate resolvedFrom = from == null
                ? resolvedTo.minusDays(29)
                : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new BusinessException(
                    "Dashboard 조회 기간이 올바르지 않습니다.",
                    LanguageLearningErrorCode.LISTENING_INVALID_STATE
            );
        }

        LearningSource source = projectionPolicy.parseSource(sourceValue);
        if (taskType != null
                && source != null
                && source != LearningSource.LISTENING) {
            throw new BusinessException(
                    "Listening taskType은 LISTENING 또는 ALL source에서만 사용할 수 있습니다.",
                    LanguageLearningErrorCode.DASHBOARD_SOURCE_INVALID
            );
        }

        String period = Math.max(
                1,
                Math.min(365, ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1)
        ) + "d";
        DashboardResponseDto legacy = legacyDashboardQueryService.get(
                userId,
                period,
                "ALL"
        );
        SourceSkillTrendResponseDto sourceTrend = sourceSkillTrendQueryService.get(
                userId,
                source,
                resolvedFrom,
                resolvedTo
        );
        DashboardInsightsResponseDto insights = insightQueryService.get(
                userId,
                source
        );
        ListeningApiContract.DashboardV3View listening = listeningDashboardFacade
                .dashboard(userId, resolvedFrom, resolvedTo, taskType);
        boolean includeListeningTrends = source == null
                || source == LearningSource.LISTENING;
        List<ListeningApiContract.MetricTrendView> listeningMetrics =
                includeListeningTrends
                        ? listeningDashboardFacade.metricTrends(
                                userId,
                                setting.getLearningLanguage(),
                                resolvedFrom,
                                resolvedTo,
                                taskType
                        )
                        : List.of();

        DashboardV3ResponseDto.IntegratedAbilityView ability =
                source == LearningSource.LISTENING && taskType != null
                        ? projectionPolicy.integratedListeningAbility(listening.metrics())
                        : projectionPolicy.integratedAbility(sourceTrend);
        List<DashboardV3ResponseDto.GrowthView> growth =
                source == LearningSource.LISTENING && taskType != null
                        ? projectionPolicy.listeningGrowth(listeningMetrics)
                        : projectionPolicy.growth(sourceTrend);
        if (source == null && taskType != null) {
            growth = mergeGrowth(
                    growth,
                    projectionPolicy.listeningGrowth(listeningMetrics)
            );
        }

        return new DashboardV3ResponseDto(
                setting.getLearningLanguage(),
                resolvedFrom,
                resolvedTo,
                source == null ? "ALL" : source.name(),
                ability,
                activityPerformance(
                        userId,
                        setting.getLearningLanguage(),
                        resolvedFrom,
                        resolvedTo,
                        today,
                        legacy,
                        listening
                ),
                growth,
                weaknesses(insights, listening.metrics(), source),
                listening.recommendations(),
                new DashboardV3ResponseDto.TrendsView(
                        sourceTrend,
                        includeListeningTrends
                                ? listening.taskTrends()
                                : List.of(),
                        listeningMetrics
                )
        );
    }

    private DashboardV3ResponseDto.ActivityPerformanceView activityPerformance(
            Long userId,
            String learningLanguage,
            LocalDate from,
            LocalDate to,
            LocalDate today,
            DashboardResponseDto legacy,
            ListeningApiContract.DashboardV3View listening
    ) {
        SourceSkillTrendResponseDto writing = sourceSkillTrendQueryService.get(
                userId, LearningSource.WRITING, from, to
        );
        SourceSkillTrendResponseDto speaking = sourceSkillTrendQueryService.get(
                userId, LearningSource.SPEAKING, from, to
        );
        SourceSkillTrendResponseDto listeningTrend = sourceSkillTrendQueryService.get(
                userId, LearningSource.LISTENING, from, to
        );
        SourceSkillTrendResponseDto reading = sourceSkillTrendQueryService.get(
                userId, LearningSource.READING, from, to
        );
        var todayListening = listeningDailySetRepository
                .findByUserIdAndLearningDateAndLearningLanguage(
                        userId,
                        today,
                        learningLanguage
                );

        int listeningMeasured = (int) listening.metrics().stream()
                .filter(value -> value.score() != null)
                .count();
        return new DashboardV3ResponseDto.ActivityPerformanceView(
                performance(
                        legacy.weeklyAverageScore(),
                        legacy.todayCompleted(),
                        legacy.todayTotal(),
                        "ITEM",
                        writing,
                        writing.metrics().size(),
                        5
                ),
                performance(
                        legacy.speakingSummary().overallAverage(),
                        legacy.speakingToday().completedMinutes(),
                        legacy.speakingToday().goalMinutes(),
                        "MINUTE",
                        speaking,
                        speaking.metrics().size(),
                        5
                ),
                performance(
                        averageListening(listening.metrics()),
                        todayListening.map(value -> (double) value.getCompletedItemCount())
                                .orElse(0.0),
                        todayListening.map(value -> (double) value.getTargetItemCount())
                                .orElse(0.0),
                        "ITEM",
                        listeningTrend,
                        listeningMeasured,
                        DashboardV3ProjectionPolicy.TOTAL_METRIC_COUNT
                ),
                performance(
                        latestAverage(reading),
                        0,
                        0,
                        "ITEM",
                        reading,
                        reading.metrics().size(),
                        DashboardV3ProjectionPolicy.TOTAL_METRIC_COUNT
                )
        );
    }

    private DashboardV3ResponseDto.ActivityPerformanceItemView performance(
            Double recentScore,
            double completed,
            double target,
            String unit,
            SourceSkillTrendResponseDto trend,
            int evaluated,
            int total
    ) {
        return new DashboardV3ResponseDto.ActivityPerformanceItemView(
                recentScore == null ? latestAverage(trend) : round(recentScore),
                new DashboardV3ResponseDto.CoverageView(evaluated, total),
                new DashboardV3ResponseDto.TodayProgressView(
                        completed,
                        target,
                        unit
                ),
                trend.sampleCount(),
                trend.collectingData()
        );
    }

    private List<DashboardV3ResponseDto.WeaknessView> weaknesses(
            DashboardInsightsResponseDto insights,
            List<ListeningApiContract.MetricProfileView> listeningProfiles,
            LearningSource source
    ) {
        Map<String, MutableWeakness> values = new LinkedHashMap<>();
        insights.weaknesses().forEach(value -> mergeWeakness(
                values,
                value.patternKey(),
                value.direction(),
                value.evidenceCount(),
                null,
                value.sources(),
                value.recommendedFocus()
        ));
        if (source == null || source == LearningSource.LISTENING) {
            listeningProfiles.stream()
                    .filter(value -> value.weaknessState()
                            == ListeningWeaknessState.ACTIVE
                            || value.weaknessState()
                            == ListeningWeaknessState.IMPROVING)
                    .forEach(value -> mergeWeakness(
                            values,
                            value.metric().name(),
                            value.weaknessState().name(),
                            value.sampleCount(),
                            value.score(),
                            List.of(LearningSource.LISTENING),
                            value.metric().name().toLowerCase(Locale.ROOT)
                    ));
        }
        return values.values().stream()
                .map(MutableWeakness::view)
                .sorted(Comparator.comparingInt(
                        DashboardV3ResponseDto.WeaknessView::evidenceCount
                ).reversed())
                .limit(10)
                .toList();
    }

    private void mergeWeakness(
            Map<String, MutableWeakness> values,
            String key,
            String state,
            int evidenceCount,
            Double recentScore,
            List<LearningSource> sources,
            String recommendedFocus
    ) {
        if (key == null || key.isBlank()) {
            return;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        MutableWeakness target = values.computeIfAbsent(
                normalized,
                ignored -> new MutableWeakness(key.trim())
        );
        target.merge(
                state,
                evidenceCount,
                recentScore,
                sources,
                recommendedFocus
        );
    }

    private List<DashboardV3ResponseDto.GrowthView> mergeGrowth(
            List<DashboardV3ResponseDto.GrowthView> base,
            List<DashboardV3ResponseDto.GrowthView> listening
    ) {
        Map<String, DashboardV3ResponseDto.GrowthView> values =
                new LinkedHashMap<>();
        base.forEach(value -> values.put(
                value.source() + ":" + value.taskType() + ":" + value.metric(),
                value
        ));
        listening.forEach(value -> values.put(
                value.source() + ":" + value.taskType() + ":" + value.metric(),
                value
        ));
        return values.values().stream()
                .sorted(Comparator.comparing(
                        DashboardV3ResponseDto.GrowthView::delta
                ).reversed())
                .toList();
    }

    private Double averageListening(
            List<ListeningApiContract.MetricProfileView> metrics
    ) {
        return metrics.stream()
                .filter(value -> value.score() != null)
                .mapToDouble(ListeningApiContract.MetricProfileView::score)
                .average().stream().boxed().findFirst()
                .map(this::round)
                .orElse(null);
    }

    private Double latestAverage(SourceSkillTrendResponseDto trend) {
        List<Double> latest = trend.metrics().values().stream()
                .filter(values -> values != null && !values.isEmpty())
                .map(values -> values.stream()
                        .max(Comparator.comparing(value -> value.date()))
                        .orElseThrow()
                        .score())
                .toList();
        return latest.isEmpty()
                ? null
                : round(latest.stream().mapToDouble(Double::doubleValue)
                        .average().orElse(0));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class MutableWeakness {
        private final String key;
        private String state = "WEAKNESS";
        private int evidenceCount;
        private Double recentScore;
        private final Set<LearningSource> sources = new LinkedHashSet<>();
        private String recommendedFocus;

        private MutableWeakness(String key) {
            this.key = key;
        }

        private void merge(
                String state,
                int evidenceCount,
                Double recentScore,
                List<LearningSource> sources,
                String recommendedFocus
        ) {
            if ("ACTIVE".equalsIgnoreCase(state)
                    || !"ACTIVE".equalsIgnoreCase(this.state)) {
                this.state = state == null ? this.state : state;
            }
            this.evidenceCount += Math.max(0, evidenceCount);
            if (recentScore != null) {
                this.recentScore = recentScore;
            }
            if (sources != null) {
                this.sources.addAll(sources);
            }
            if (recommendedFocus != null && !recommendedFocus.isBlank()) {
                this.recommendedFocus = recommendedFocus;
            }
        }

        private DashboardV3ResponseDto.WeaknessView view() {
            return new DashboardV3ResponseDto.WeaknessView(
                    key,
                    state,
                    evidenceCount,
                    recentScore,
                    new ArrayList<>(sources),
                    recommendedFocus
            );
        }
    }
}
