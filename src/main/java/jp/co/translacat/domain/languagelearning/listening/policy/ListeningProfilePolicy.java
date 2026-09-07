package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceLevel;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvidenceBand;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningWeaknessState;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListeningProfilePolicy {

    public static final String VERSION = "listening-profile";
    public static final double MIN_CONFIDENCE = 0.70;
    public static final int MAX_ACTIVITIES = 30;

    public double recencyWeight(int rank) {
        if (rank < 1 || rank > MAX_ACTIVITIES) {
            return 0;
        }
        if (rank <= 5) {
            return 1.00;
        }
        if (rank <= 10) {
            return 0.85;
        }
        if (rank <= 20) {
            return 0.70;
        }
        return 0.55;
    }

    public double assistanceWeight(ListeningAssistanceLevel level) {
        if (level == null || level == ListeningAssistanceLevel.INDEPENDENT) {
            return 1.00;
        }
        if (level == ListeningAssistanceLevel.ASSISTED) {
            return 0.85;
        }
        return 0.0;
    }

    public double finalWeight(
            int recencyRank,
            double confidence,
            ListeningAssistanceLevel assistanceLevel,
            double evidenceWeight
    ) {
        if (confidence < MIN_CONFIDENCE || evidenceWeight <= 0) {
            return 0;
        }
        return recencyWeight(recencyRank)
                * clamp01(confidence)
                * assistanceWeight(assistanceLevel)
                * clamp01(evidenceWeight);
    }

    public ProfileAggregate aggregate(List<Signal> source) {
        List<Signal> values = eligibleDistinct(source);
        if (values.isEmpty()) {
            return ProfileAggregate.dataCollecting();
        }
        double numerator = 0;
        double denominator = 0;
        for (int index = 0; index < values.size(); index++) {
            Signal signal = values.get(index);
            double weight = finalWeight(
                    index + 1,
                    signal.confidence(),
                    signal.assistanceLevel(),
                    signal.evidenceWeight()
            );
            numerator += signal.rawScore() * weight;
            denominator += weight;
        }
        if (denominator == 0 || values.size() < 3) {
            return new ProfileAggregate(
                    null,
                    values.size(),
                    "DATA_COLLECTING"
            );
        }
        return new ProfileAggregate(
                numerator / denominator,
                values.size(),
                confidenceLabel(values.size())
        );
    }

    public GrowthResult growth(
            List<Signal> source,
            boolean currentlyActive
    ) {
        List<Signal> values = eligibleDistinct(source);
        if (values.size() < 6) {
            return GrowthResult.collecting(values.size());
        }
        List<Signal> recent = values.subList(0, Math.min(5, values.size()));
        List<Signal> previous = values.subList(
                recent.size(),
                Math.min(recent.size() + 5, values.size())
        );
        if (recent.size() < 3 || previous.size() < 3) {
            return GrowthResult.collecting(values.size());
        }
        double recentAverage = windowAverage(recent);
        double previousAverage = windowAverage(previous);
        double delta = recentAverage - previousAverage;
        return new GrowthResult(
                delta >= (currentlyActive ? 3.0 : 5.0),
                previousAverage,
                recentAverage,
                delta,
                recent.size() + previous.size()
        );
    }

    public WeaknessResult weakness(List<Signal> source) {
        List<Signal> recent = eligibleDistinct(source).stream()
                .limit(5)
                .toList();
        if (recent.size() < 3) {
            return new WeaknessResult(
                    ListeningWeaknessState.DATA_COLLECTING,
                    recent.size(),
                    0,
                    0,
                    null
            );
        }
        int weakCount = (int) recent.stream()
                .filter(value -> classify(value.rawScore())
                        == ListeningEvidenceBand.WEAK_EVIDENCE)
                .count();
        int recoveryCount = (int) recent.stream()
                .filter(value -> classify(value.rawScore())
                        == ListeningEvidenceBand.RECOVERY_EVIDENCE)
                .count();
        double average = recent.stream()
                .mapToDouble(Signal::rawScore)
                .average()
                .orElse(0);
        ListeningWeaknessState state;
        if (recoveryCount >= 4 && weakCount == 0 && average >= 75) {
            state = ListeningWeaknessState.RESOLVED;
        } else if (recoveryCount >= 3 && average >= 70) {
            state = ListeningWeaknessState.IMPROVING;
        } else if (weakCount >= 2) {
            state = ListeningWeaknessState.ACTIVE;
        } else {
            state = ListeningWeaknessState.DATA_COLLECTING;
        }
        return new WeaknessResult(
                state,
                recent.size(),
                weakCount,
                recoveryCount,
                average
        );
    }

    public ListeningEvidenceBand classify(double score) {
        if (score < 65) {
            return ListeningEvidenceBand.WEAK_EVIDENCE;
        }
        if (score < 75) {
            return ListeningEvidenceBand.NEUTRAL;
        }
        return ListeningEvidenceBand.RECOVERY_EVIDENCE;
    }

    private List<Signal> eligibleDistinct(List<Signal> source) {
        if (source == null) {
            return List.of();
        }
        List<Signal> ordered = source.stream()
                .filter(Signal::eligible)
                .sorted(Comparator.comparing(Signal::createdAt).reversed())
                .toList();
        Map<String, Signal> distinct = new LinkedHashMap<>();
        for (Signal signal : ordered) {
            distinct.putIfAbsent(signal.activityId(), signal);
            if (distinct.size() == MAX_ACTIVITIES) {
                break;
            }
        }
        return new ArrayList<>(distinct.values());
    }

    private double windowAverage(List<Signal> values) {
        double numerator = 0;
        double denominator = 0;
        for (Signal signal : values) {
            double weight = clamp01(signal.confidence())
                    * assistanceWeight(signal.assistanceLevel())
                    * clamp01(signal.evidenceWeight());
            numerator += signal.rawScore() * weight;
            denominator += weight;
        }
        return denominator == 0 ? 0 : numerator / denominator;
    }

    private double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
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

    public record Signal(
            String activityId,
            double rawScore,
            double confidence,
            ListeningAssistanceLevel assistanceLevel,
            double evidenceWeight,
            boolean official,
            boolean practice,
            boolean excluded,
            LocalDateTime createdAt
    ) {
        public boolean eligible() {
            return official
                    && !practice
                    && !excluded
                    && confidence >= MIN_CONFIDENCE
                    && evidenceWeight > 0
                    && createdAt != null;
        }
    }

    public record ProfileAggregate(
            Double score,
            int sampleCount,
            String confidence
    ) {
        public static ProfileAggregate dataCollecting() {
            return new ProfileAggregate(null, 0, "DATA_COLLECTING");
        }
    }

    public record GrowthResult(
            boolean active,
            Double previousAverage,
            Double recentAverage,
            Double delta,
            int sampleCount
    ) {
        public static GrowthResult collecting(int sampleCount) {
            return new GrowthResult(
                    false,
                    null,
                    null,
                    null,
                    sampleCount
            );
        }
    }

    public record WeaknessResult(
            ListeningWeaknessState state,
            int sampleCount,
            int weakCount,
            int recoveryCount,
            Double recentAverage
    ) {
    }
}
