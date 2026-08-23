package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningMetricType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ListeningEvaluationContractPolicy {

    private static final double EPSILON = 0.000_001;

    private static final Map<ListeningTaskType, Map<ListeningMetricType, Double>>
            METRIC_WEIGHTS = metricWeights();
    private static final Map<ListeningTaskType, Map<ListeningProfileMetric, Double>>
            PROFILE_WEIGHTS = profileWeights();

    public Map<ListeningMetricType, Double> expectedMetricWeights(
            ListeningTaskType taskType
    ) {
        return METRIC_WEIGHTS.getOrDefault(taskType, Map.of());
    }

    public Map<ListeningProfileMetric, Double> allowedProfileSignals(
            ListeningTaskType taskType
    ) {
        return PROFILE_WEIGHTS.getOrDefault(taskType, Map.of());
    }

    public void validateMetrics(
            ListeningTaskType taskType,
            Collection<MetricProjection> metrics
    ) {
        Map<ListeningMetricType, Double> expected =
                expectedMetricWeights(taskType);
        if (expected.isEmpty() || metrics == null) {
            throw schema("Metric 목록이 필요합니다.");
        }
        Map<ListeningMetricType, MetricProjection> actual;
        try {
            actual = metrics.stream().collect(Collectors.toMap(
                    MetricProjection::type,
                    Function.identity()
            ));
        } catch (RuntimeException exception) {
            throw schema("Metric이 중복되었습니다.");
        }
        if (!actual.keySet().equals(expected.keySet())) {
            throw schema("Task별 Metric Allowlist와 일치하지 않습니다.");
        }
        expected.forEach((type, weight) -> {
            MetricProjection value = actual.get(type);
            if (value == null
                    || Math.abs(value.weight() - weight) > EPSILON) {
                throw schema("Task별 고정 Metric Weight와 일치하지 않습니다.");
            }
        });
    }

    public void validateProfileSignals(
            ListeningTaskType taskType,
            Collection<ProfileSignalProjection> signals
    ) {
        if (signals == null || signals.isEmpty()) {
            return;
        }
        Map<ListeningProfileMetric, Double> expected =
                allowedProfileSignals(taskType);
        EnumSet<ListeningProfileMetric> seen =
                EnumSet.noneOf(ListeningProfileMetric.class);
        for (ProfileSignalProjection signal : signals) {
            Double weight = expected.get(signal.metric());
            if (!seen.add(signal.metric())
                    || weight == null
                    || Math.abs(weight - signal.evidenceWeight()) > EPSILON) {
                throw schema(
                        "허용되지 않은 Profile Signal 또는 Evidence Weight입니다."
                );
            }
        }
    }

    public boolean profileEligible(
            boolean official,
            boolean practice,
            boolean answerRevealed,
            boolean excluded,
            boolean evaluable,
            Double score,
            Double confidence,
            double evidenceWeight
    ) {
        return official
                && !practice
                && !answerRevealed
                && !excluded
                && evaluable
                && score != null
                && confidence != null
                && confidence >= ListeningProfilePolicy.MIN_CONFIDENCE
                && evidenceWeight > 0;
    }

    private BusinessException schema(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.AI_SCHEMA_INVALID
        );
    }

    private static Map<ListeningTaskType, Map<ListeningMetricType, Double>>
    metricWeights() {
        EnumMap<ListeningTaskType, Map<ListeningMetricType, Double>> values =
                new EnumMap<>(ListeningTaskType.class);
        values.put(
                ListeningTaskType.DICTATION,
                Map.of(
                        ListeningMetricType.TOKEN_RECOGNITION, 0.60,
                        ListeningMetricType.OMISSION_ADDITION_ORDER, 0.25,
                        ListeningMetricType.ORTHOGRAPHY, 0.15
                )
        );
        values.put(
                ListeningTaskType.INTERPRETATION,
                Map.of(
                        ListeningMetricType.MEANING_FIDELITY, 0.60,
                        ListeningMetricType.DETAIL_AND_NUANCE, 0.25,
                        ListeningMetricType.ORIGIN_NATURALNESS, 0.15
                )
        );
        values.put(
                ListeningTaskType.REPEAT_AFTER_AUDIO,
                Map.of(
                        ListeningMetricType.PRONUNCIATION, 0.40,
                        ListeningMetricType.PROSODY_RHYTHM, 0.25,
                        ListeningMetricType.FLUENCY, 0.20,
                        ListeningMetricType.COMPLETENESS, 0.15
                )
        );
        return Map.copyOf(values);
    }

    private static Map<ListeningTaskType, Map<ListeningProfileMetric, Double>>
    profileWeights() {
        EnumMap<ListeningTaskType, Map<ListeningProfileMetric, Double>> values =
                new EnumMap<>(ListeningTaskType.class);
        values.put(
                ListeningTaskType.DICTATION,
                Map.of(
                        ListeningProfileMetric.LISTENING_RECOGNITION, 1.00,
                        ListeningProfileMetric.VOCABULARY, 0.60,
                        ListeningProfileMetric.ORTHOGRAPHY, 0.80
                )
        );
        values.put(
                ListeningTaskType.INTERPRETATION,
                Map.of(
                        ListeningProfileMetric.MEANING, 1.00,
                        ListeningProfileMetric.ORIGIN_NATURALNESS, 0.30
                )
        );
        values.put(
                ListeningTaskType.REPEAT_AFTER_AUDIO,
                Map.of(
                        ListeningProfileMetric.PRONUNCIATION, 1.00,
                        ListeningProfileMetric.FLUENCY, 0.80,
                        ListeningProfileMetric.LISTENING_RECOGNITION, 0.50
                )
        );
        return Map.copyOf(values);
    }

    public record MetricProjection(
            ListeningMetricType type,
            double weight
    ) {
    }

    public record ProfileSignalProjection(
            ListeningProfileMetric metric,
            double evidenceWeight
    ) {
    }
}
