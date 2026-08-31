package jp.co.translacat.domain.languagelearning.level.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LevelTestAdaptivePolicyTest {

    private final LevelTestAdaptivePolicy policy =
            new LevelTestAdaptivePolicy();

    @Test
    void appliesBoundaryRules() {
        assertThat(policy.initialBand(null, false)).isEqualTo(2);
        assertThat(policy.afterEvaluated(3, 80)).isEqualTo(4);
        assertThat(policy.afterEvaluated(3, 55)).isEqualTo(3);
        assertThat(policy.afterEvaluated(3, 54)).isEqualTo(2);
        assertThat(policy.afterObjective(5, true)).isEqualTo(5);
        assertThat(policy.afterObjective(1, false)).isEqualTo(1);
    }

    @Test
    void exposesOnlyReachableNextBands() {
        assertThat(policy.objectiveCandidateBands(1)).containsExactly(1, 2);
        assertThat(policy.objectiveCandidateBands(3)).containsExactly(2, 4);
        assertThat(policy.objectiveCandidateBands(5)).containsExactly(4, 5);
        assertThat(policy.candidateBands(3)).containsExactly(2, 3, 4);
    }
}
