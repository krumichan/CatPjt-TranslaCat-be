package jp.co.translacat.domain.languagelearning.listening.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListeningIndependencePolicyTest {

    private final ListeningIndependencePolicy policy =
            new ListeningIndependencePolicy();

    @Test
    void calculatesIndependenceScore() {
        assertThat(policy.score(1, 0)).isEqualTo(100);
        assertThat(policy.score(2, 0)).isEqualTo(95);
        assertThat(policy.score(3, 1)).isEqualTo(80);
        assertThat(policy.score(20, 20)).isEqualTo(60);
    }

    @Test
    void adjustsOverallScore() {
        assertThat(policy.adjustedOverall(91, 80)).isEqualTo(89);
    }
}
