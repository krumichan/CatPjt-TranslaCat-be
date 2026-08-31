package jp.co.translacat.domain.languagelearning.level.policy;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class LevelTestScoringPolicy {

    public static final String VERSION = "LEVEL_TEST_SCORING_V1";

    private static final Map<LevelTestDomain, BigDecimal> WEIGHTS = Map.of(
            LevelTestDomain.VOCABULARY, new BigDecimal("0.10"),
            LevelTestDomain.GRAMMAR, new BigDecimal("0.10"),
            LevelTestDomain.READING, new BigDecimal("0.20"),
            LevelTestDomain.LISTENING, new BigDecimal("0.20"),
            LevelTestDomain.WRITING, new BigDecimal("0.20"),
            LevelTestDomain.SPEAKING, new BigDecimal("0.20")
    );

    public int domainScore(List<Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            throw new IllegalArgumentException("scores are required");
        }

        BigDecimal total = scores.stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(
                BigDecimal.valueOf(scores.size()),
                0,
                RoundingMode.HALF_UP
        ).intValueExact();
    }

    public int overall(Map<LevelTestDomain, Integer> domainScores) {
        if (domainScores == null
                || domainScores.size() != LevelTestDomain.values().length) {
            throw new IllegalArgumentException(
                    "all domain scores are required"
            );
        }

        BigDecimal total = BigDecimal.ZERO;
        for (LevelTestDomain domain : LevelTestDomain.values()) {
            total = total.add(
                    BigDecimal.valueOf(domainScores.get(domain))
                            .multiply(WEIGHTS.get(domain))
            );
        }
        return total.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    public String band(int score) {
        if (score < 40) {
            return "FOUNDATION";
        }
        if (score < 55) {
            return "BASIC";
        }
        if (score < 70) {
            return "INTERMEDIATE";
        }
        if (score < 85) {
            return "UPPER_INTERMEDIATE";
        }
        return "ADVANCED";
    }

    public Map<LevelTestDomain, Integer> weightsPercent() {
        Map<LevelTestDomain, Integer> result =
                new EnumMap<>(LevelTestDomain.class);
        WEIGHTS.forEach((domain, weight) -> result.put(
                domain,
                weight.multiply(BigDecimal.valueOf(100)).intValue()
        ));
        return result;
    }
}
