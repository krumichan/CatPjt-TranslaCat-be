package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.MetricPointResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.SourceSkillTrendResponseDto;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DashboardProjectionPolicy {

    public static final int TOTAL_METRIC_COUNT = 10;
    private static final double GROWTH_THRESHOLD = 5.0;

    public LearningSource parseSource(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return LearningSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "Dashboard source filter가 올바르지 않습니다.",
                    LanguageLearningErrorCode.DASHBOARD_SOURCE_INVALID
            );
        }
    }

    public DashboardResponseDto.IntegratedAbilityView integratedAbility(
            SourceSkillTrendResponseDto trend
    ) {
        List<DashboardResponseDto.AbilityMetricView> metrics = trend.metrics()
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> abilityMetric(entry.getKey(), entry.getValue(), trend))
                .toList();
        return integratedAbility(metrics);
    }

    public DashboardResponseDto.IntegratedAbilityView integratedListeningAbility(
            List<ListeningApiContract.MetricProfileView> profiles
    ) {
        List<DashboardResponseDto.AbilityMetricView> metrics = profiles.stream()
                .filter(value -> value.score() != null)
                .map(value -> new DashboardResponseDto.AbilityMetricView(
                        value.metric().name(),
                        round(value.score()),
                        value.sampleCount(),
                        value.confidence(),
                        value.sampleCount() < 3 || value.score() == null
                ))
                .sorted(Comparator.comparing(
                        DashboardResponseDto.AbilityMetricView::metric
                ))
                .toList();
        return integratedAbility(metrics);
    }

    public List<DashboardResponseDto.GrowthView> growth(
            SourceSkillTrendResponseDto trend
    ) {
        List<DashboardResponseDto.GrowthView> result = new ArrayList<>();
        trend.metrics().forEach((metric, values) -> {
            List<ScorePoint> points = values.stream()
                    .map(value -> new ScorePoint(
                            value.date(), value.score(), 1
                    ))
                    .toList();
            GrowthWindow window = growthWindow(points);
            if (window.active()) {
                result.add(new DashboardResponseDto.GrowthView(
                        metric,
                        trend.source(),
                        null,
                        window.previousAverage(),
                        window.recentAverage(),
                        window.delta(),
                        window.previousSampleCount(),
                        window.recentSampleCount()
                ));
            }
        });
        return result.stream()
                .sorted(Comparator.comparing(
                        DashboardResponseDto.GrowthView::delta
                ).reversed())
                .toList();
    }

    public List<DashboardResponseDto.GrowthView> listeningGrowth(
            List<ListeningApiContract.MetricTrendView> trends
    ) {
        record Key(
                jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType task,
                String metric
        ) {
        }
        Map<Key, List<ScorePoint>> grouped = new LinkedHashMap<>();
        for (ListeningApiContract.MetricTrendView value : trends) {
            if (value.averageScore() == null) {
                continue;
            }
            grouped.computeIfAbsent(
                    new Key(value.taskType(), value.metric()),
                    ignored -> new ArrayList<>()
            ).add(new ScorePoint(
                    value.date(),
                    value.averageScore(),
                    Math.max(1, value.sampleCount())
            ));
        }
        List<DashboardResponseDto.GrowthView> result = new ArrayList<>();
        grouped.forEach((key, values) -> {
            GrowthWindow window = growthWindow(values);
            if (window.active()) {
                result.add(new DashboardResponseDto.GrowthView(
                        key.metric(),
                        LearningSource.LISTENING.name(),
                        key.task(),
                        window.previousAverage(),
                        window.recentAverage(),
                        window.delta(),
                        window.previousSampleCount(),
                        window.recentSampleCount()
                ));
            }
        });
        return result.stream()
                .sorted(Comparator.comparing(
                        DashboardResponseDto.GrowthView::delta
                ).reversed())
                .toList();
    }

    private DashboardResponseDto.IntegratedAbilityView integratedAbility(
            List<DashboardResponseDto.AbilityMetricView> metrics
    ) {
        List<DashboardResponseDto.AbilityMetricView> measured = metrics.stream()
                .filter(value -> value.score() != null)
                .toList();
        Double overall = measured.isEmpty()
                ? null
                : round(measured.stream()
                        .mapToDouble(DashboardResponseDto.AbilityMetricView::score)
                        .average()
                        .orElse(0));
        String confidence = aggregateConfidence(measured);
        return new DashboardResponseDto.IntegratedAbilityView(
                overall,
                confidence,
                measured.size(),
                TOTAL_METRIC_COUNT,
                groups(measured),
                List.copyOf(metrics)
        );
    }

    private DashboardResponseDto.AbilityMetricView abilityMetric(
            String metric,
            List<MetricPointResponseDto> points,
            SourceSkillTrendResponseDto trend
    ) {
        MetricPointResponseDto latest = points.stream()
                .max(Comparator.comparing(MetricPointResponseDto::date))
                .orElse(null);
        boolean collecting = trend.collectingData() || points.size() < 3;
        return new DashboardResponseDto.AbilityMetricView(
                metric,
                latest == null ? null : round(latest.score()),
                points.size(),
                collecting ? "DATA_COLLECTING" : confidenceLabel(points.size()),
                collecting
        );
    }

    private List<DashboardResponseDto.AbilityGroupView> groups(
            List<DashboardResponseDto.AbilityMetricView> metrics
    ) {
        Map<String, List<Double>> grouped = new LinkedHashMap<>();
        for (DashboardResponseDto.AbilityMetricView metric : metrics) {
            grouped.computeIfAbsent(
                    group(metric.metric()),
                    ignored -> new ArrayList<>()
            ).add(metric.score());
        }
        return grouped.entrySet().stream()
                .map(entry -> new DashboardResponseDto.AbilityGroupView(
                        entry.getKey(),
                        round(entry.getValue().stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElse(0)),
                        entry.getValue().size()
                ))
                .toList();
    }

    private String group(String metric) {
        String value = metric.toUpperCase(Locale.ROOT);
        if (value.contains("LISTENING")
                || value.equals("MEANING")
                || value.equals("VOCABULARY")) {
            return "COMPREHENSION";
        }
        if (value.contains("PRONUNCIATION")
                || value.contains("FLUENCY")
                || value.contains("INTERACTION")) {
            return "SPEECH";
        }
        if (value.contains("EXPRESSION")) {
            return "EXPRESSION";
        }
        return "ACCURACY";
    }

    private GrowthWindow growthWindow(List<ScorePoint> source) {
        List<ScorePoint> ordered = source.stream()
                .sorted(Comparator.comparing(ScorePoint::date).reversed())
                .toList();
        if (ordered.size() < 6) {
            return GrowthWindow.collecting();
        }
        List<ScorePoint> recent = ordered.subList(0, Math.min(5, ordered.size()));
        List<ScorePoint> previous = ordered.subList(
                recent.size(),
                Math.min(recent.size() + 5, ordered.size())
        );
        int recentSamples = sampleCount(recent);
        int previousSamples = sampleCount(previous);
        if (recentSamples < 3 || previousSamples < 3) {
            return GrowthWindow.collecting();
        }
        double recentAverage = weightedAverage(recent);
        double previousAverage = weightedAverage(previous);
        double delta = round(recentAverage - previousAverage);
        return new GrowthWindow(
                delta >= GROWTH_THRESHOLD,
                round(previousAverage),
                round(recentAverage),
                delta,
                previousSamples,
                recentSamples
        );
    }

    private double weightedAverage(List<ScorePoint> values) {
        double denominator = values.stream().mapToInt(ScorePoint::sampleCount).sum();
        if (denominator <= 0) {
            return 0;
        }
        double numerator = values.stream()
                .mapToDouble(value -> value.score() * value.sampleCount())
                .sum();
        return numerator / denominator;
    }

    private int sampleCount(List<ScorePoint> values) {
        return values.stream().mapToInt(ScorePoint::sampleCount).sum();
    }

    private String aggregateConfidence(
            List<DashboardResponseDto.AbilityMetricView> metrics
    ) {
        if (metrics.isEmpty()) {
            return "DATA_COLLECTING";
        }
        if (metrics.stream().allMatch(value -> !value.collectingData())
                && metrics.size() >= 8) {
            return "HIGH";
        }
        if (metrics.size() >= 5) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String confidenceLabel(int samples) {
        if (samples >= 10) {
            return "HIGH";
        }
        if (samples >= 5) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ScorePoint(
            java.time.LocalDate date,
            double score,
            int sampleCount
    ) {
    }

    private record GrowthWindow(
            boolean active,
            Double previousAverage,
            Double recentAverage,
            Double delta,
            int previousSampleCount,
            int recentSampleCount
    ) {
        private static GrowthWindow collecting() {
            return new GrowthWindow(false, null, null, null, 0, 0);
        }
    }
}
