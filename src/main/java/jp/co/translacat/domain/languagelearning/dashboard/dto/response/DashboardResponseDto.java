package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponseDto(
        String learningLanguage,
        LocalDate from,
        LocalDate to,
        String source,
        IntegratedAbilityView integratedAbility,
        ActivityPerformanceView activityPerformance,
        List<GrowthView> growth,
        List<WeaknessView> weaknesses,
        List<ListeningApiContract.RecommendationView> recommendations,
        TrendsView trends
) {

    public record IntegratedAbilityView(
            Double overall,
            String confidence,
            int measuredMetricCount,
            int totalMetricCount,
            List<AbilityGroupView> groups,
            List<AbilityMetricView> metrics
    ) {
    }

    public record AbilityGroupView(
            String group,
            Double score,
            int measuredMetricCount
    ) {
    }

    public record AbilityMetricView(
            String metric,
            Double score,
            int sampleCount,
            String confidence,
            boolean collectingData
    ) {
    }

    public record ActivityPerformanceView(
            ActivityPerformanceItemView writing,
            ActivityPerformanceItemView speaking,
            ActivityPerformanceItemView listening,
            ActivityPerformanceItemView reading
    ) {
    }

    public record ActivityPerformanceItemView(
            Double recentScore,
            CoverageView coverage,
            TodayProgressView today,
            int sampleCount,
            boolean collectingData
    ) {
    }

    public record CoverageView(int evaluated, int total) {
    }

    public record TodayProgressView(
            double completed,
            double target,
            String unit
    ) {
    }

    public record GrowthView(
            String metric,
            String source,
            ListeningTaskType taskType,
            Double previousAverage,
            Double recentAverage,
            Double delta,
            int previousSampleCount,
            int recentSampleCount
    ) {
    }

    public record WeaknessView(
            String key,
            String state,
            int evidenceCount,
            Double recentScore,
            List<LearningSource> sources,
            String recommendedFocus
    ) {
    }

    public record TrendsView(
            SourceSkillTrendResponseDto sourceMetrics,
            List<ListeningApiContract.TaskTrendView> listeningTasks,
            List<ListeningApiContract.MetricTrendView> listeningMetrics
    ) {
    }
}
