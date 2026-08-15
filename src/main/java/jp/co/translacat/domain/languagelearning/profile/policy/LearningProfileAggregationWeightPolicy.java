package jp.co.translacat.domain.languagelearning.profile.policy;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LearningProfileAggregationWeightPolicy {

    public static final String POLICY_VERSION = "learning-profile-aggregation-v1";
    public static final int MAX_EVALUATED_ACTIVITIES = 30;
    public static final int MIN_SOURCE_EVIDENCE = 2;
    public static final int MIN_UNIFIED_EVIDENCE = 3;
    public static final int COLLECTING_DATA_THRESHOLD = 3;

    private static final double OLDEST_RECENCY_WEIGHT = 0.50;

    public double recencyWeight(int index, int total) {
        if (total <= 1) {
            return 1.0;
        }
        double ratio = (double) index / (total - 1);
        return round(1.0 - ((1.0 - OLDEST_RECENCY_WEIGHT) * ratio));
    }

    public double speakingActivityWeight(
            int index,
            int total,
            Double evaluationConfidence,
            double validity,
            List<AssistanceType> assistance,
            SpeakingMetricType metricType
    ) {
        double confidence = clamp(evaluationConfidence == null
                ? 0.0
                : evaluationConfidence);
        return round(
                recencyWeight(index, total)
                        * confidence
                        * clamp(validity)
                        * assistanceWeight(assistance, metricType)
        );
    }

    public double writingActivityWeight(int index, int total) {
        return recencyWeight(index, total);
    }

    public boolean isSourceEstablished(int evidenceCount) {
        return evidenceCount >= MIN_SOURCE_EVIDENCE;
    }

    public boolean isUnified(int sourceCount, int evidenceCount) {
        return sourceCount >= 2 && evidenceCount >= MIN_UNIFIED_EVIDENCE;
    }

    private double assistanceWeight(
            List<AssistanceType> assistance,
            SpeakingMetricType metricType
    ) {
        if (metricType == SpeakingMetricType.FLUENCY
                || metricType == SpeakingMetricType.PRONUNCIATION) {
            return 1.0;
        }
        if (assistance == null || assistance.isEmpty()) {
            return 1.0;
        }
        if (assistance.contains(AssistanceType.SAMPLE_ANSWER)) {
            return 0.60;
        }
        if (assistance.contains(AssistanceType.HINT)
                || assistance.contains(AssistanceType.TRANSLATION)) {
            return 0.80;
        }
        return 1.0;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
