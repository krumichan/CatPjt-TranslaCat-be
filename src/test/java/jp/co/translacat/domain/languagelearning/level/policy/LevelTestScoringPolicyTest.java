package jp.co.translacat.domain.languagelearning.level.policy;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;

class LevelTestScoringPolicyTest {

    private final LevelTestScoringPolicy policy =
            new LevelTestScoringPolicy();

    @Test
    void calculatesWeightedOverallScore() {
        var scores = new EnumMap<LevelTestDomain, Integer>(
                LevelTestDomain.class
        );
        scores.put(LevelTestDomain.VOCABULARY, 50);
        scores.put(LevelTestDomain.GRAMMAR, 60);
        scores.put(LevelTestDomain.READING, 70);
        scores.put(LevelTestDomain.LISTENING, 80);
        scores.put(LevelTestDomain.WRITING, 90);
        scores.put(LevelTestDomain.SPEAKING, 100);

        assertThat(policy.overall(scores)).isEqualTo(79);
    }

    @Test
    void mapsBandBoundaries() {
        assertThat(policy.band(39)).isEqualTo("FOUNDATION");
        assertThat(policy.band(40)).isEqualTo("BASIC");
        assertThat(policy.band(55)).isEqualTo("INTERMEDIATE");
        assertThat(policy.band(70)).isEqualTo("UPPER_INTERMEDIATE");
        assertThat(policy.band(85)).isEqualTo("ADVANCED");
    }
}
