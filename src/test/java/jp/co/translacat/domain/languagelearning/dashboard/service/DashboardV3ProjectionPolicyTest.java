package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.MetricPointResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.SourceSkillTrendResponseDto;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardV3ProjectionPolicyTest {

    private final DashboardV3ProjectionPolicy policy =
            new DashboardV3ProjectionPolicy();

    @Test
    void supportsAllDashboardSourceFilters() {
        assertThat(policy.parseSource("ALL")).isNull();
        assertThat(policy.parseSource("writing")).isEqualTo(LearningSource.WRITING);
        assertThat(policy.parseSource("SPEAKING")).isEqualTo(LearningSource.SPEAKING);
        assertThat(policy.parseSource("LISTENING")).isEqualTo(LearningSource.LISTENING);
        assertThat(policy.parseSource("READING")).isEqualTo(LearningSource.READING);
    }

    @Test
    void rejectsUnknownDashboardSourceFilter() {
        assertThatThrownBy(() -> policy.parseSource("UNKNOWN"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                LanguageLearningErrorCode.DASHBOARD_SOURCE_INVALID
                        ));
    }

    @Test
    void growthActivatesAtExactlyFivePoints() {
        var trend = trend(List.of(
                70.0, 70.0, 70.0, 70.0, 70.0,
                75.0, 75.0, 75.0, 75.0, 75.0
        ));

        var growth = policy.growth(trend);

        assertThat(growth).hasSize(1);
        assertThat(growth.getFirst().delta()).isEqualTo(5.0);
        assertThat(growth.getFirst().previousSampleCount()).isEqualTo(5);
        assertThat(growth.getFirst().recentSampleCount()).isEqualTo(5);
    }

    @Test
    void growthDoesNotActivateBelowFivePoints() {
        var trend = trend(List.of(
                70.0, 70.0, 70.0, 70.0, 70.0,
                74.99, 74.99, 74.99, 74.99, 74.99
        ));

        assertThat(policy.growth(trend)).isEmpty();
    }

    @Test
    void missingMetricsAreNotFilledWithZero() {
        SourceSkillTrendResponseDto trend = new SourceSkillTrendResponseDto(
                "LISTENING",
                3,
                0.9,
                false,
                Map.of(
                        "MEANING",
                        List.of(
                                new MetricPointResponseDto(
                                        LocalDate.of(2026, 8, 21), 80
                                ),
                                new MetricPointResponseDto(
                                        LocalDate.of(2026, 8, 22), 82
                                ),
                                new MetricPointResponseDto(
                                        LocalDate.of(2026, 8, 23), 84
                                )
                        )
                )
        );

        var ability = policy.integratedAbility(trend);

        assertThat(ability.measuredMetricCount()).isEqualTo(1);
        assertThat(ability.totalMetricCount()).isEqualTo(10);
        assertThat(ability.metrics()).extracting(value -> value.metric())
                .containsExactly("MEANING");
        assertThat(ability.metrics().getFirst().score()).isEqualTo(84.0);
    }

    private SourceSkillTrendResponseDto trend(List<Double> values) {
        Map<String, List<MetricPointResponseDto>> metrics = new LinkedHashMap<>();
        LocalDate start = LocalDate.of(2026, 8, 1);
        metrics.put(
                "MEANING",
                java.util.stream.IntStream.range(0, values.size())
                        .mapToObj(index -> new MetricPointResponseDto(
                                start.plusDays(index),
                                values.get(index)
                        ))
                        .toList()
        );
        return new SourceSkillTrendResponseDto(
                "ALL",
                values.size(),
                0.9,
                false,
                metrics
        );
    }
}
