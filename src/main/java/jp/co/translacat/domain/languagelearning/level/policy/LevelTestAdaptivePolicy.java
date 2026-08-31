package jp.co.translacat.domain.languagelearning.level.policy;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

@Component
public class LevelTestAdaptivePolicy {

    public int initialBand(Double baseLevelScore, boolean recheck) {
        if (!recheck || baseLevelScore == null) {
            return 2;
        }
        if (baseLevelScore < 40) {
            return 1;
        }
        if (baseLevelScore < 55) {
            return 2;
        }
        if (baseLevelScore < 70) {
            return 3;
        }
        if (baseLevelScore < 85) {
            return 4;
        }
        return 5;
    }

    public int afterObjective(int current, boolean correct) {
        return clamp(current + (correct ? 1 : -1));
    }

    public int afterEvaluated(int current, Integer score) {
        if (score == null) {
            return clamp(current);
        }
        if (score >= 80) {
            return clamp(current + 1);
        }
        if (score < 55) {
            return clamp(current - 1);
        }
        return clamp(current);
    }

    public List<Integer> objectiveCandidateBands(int current) {
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        values.add(afterObjective(current, false));
        values.add(afterObjective(current, true));
        return List.copyOf(values);
    }

    public List<Integer> candidateBands(int current) {
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        values.add(clamp(current - 1));
        values.add(clamp(current));
        values.add(clamp(current + 1));
        return List.copyOf(values);
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(5, value));
    }
}
