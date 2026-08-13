package jp.co.translacat.domain.languagelearning.daily.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DailyWritingDifficultyPolicyTest {

    private final DailyWritingDifficultyPolicy policy =
            new DailyWritingDifficultyPolicy();

    @Test
    void fiveSentencesUseOneThreeOne() {
        var distribution = policy.distribute(5);

        assertThat(distribution.review()).isEqualTo(1);
        assertThat(distribution.normal()).isEqualTo(3);
        assertThat(distribution.challenge()).isEqualTo(1);
    }

    @Test
    void smallSetsStillHaveValidTotal() {
        for (int count = 1; count <= 20; count++) {
            var distribution = policy.distribute(count);

            assertThat(
                    distribution.review()
                            + distribution.normal()
                            + distribution.challenge()
            ).isEqualTo(count);
            assertThat(distribution.normal()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void twentySentencesKeepTwentySixtyTwenty() {
        var distribution = policy.distribute(20);

        assertThat(distribution.review()).isEqualTo(4);
        assertThat(distribution.normal()).isEqualTo(12);
        assertThat(distribution.challenge()).isEqualTo(4);
    }
}
