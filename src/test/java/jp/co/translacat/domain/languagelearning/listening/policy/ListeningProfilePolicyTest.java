package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceLevel;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvidenceBand;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningWeaknessState;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListeningProfilePolicyTest {

    private final ListeningProfilePolicy policy = new ListeningProfilePolicy();

    @Test
    void appliesFixedRecencyAndAssistanceBoundaries() {
        assertThat(policy.recencyWeight(1)).isEqualTo(1.00);
        assertThat(policy.recencyWeight(5)).isEqualTo(1.00);
        assertThat(policy.recencyWeight(6)).isEqualTo(0.85);
        assertThat(policy.recencyWeight(10)).isEqualTo(0.85);
        assertThat(policy.recencyWeight(11)).isEqualTo(0.70);
        assertThat(policy.recencyWeight(20)).isEqualTo(0.70);
        assertThat(policy.recencyWeight(21)).isEqualTo(0.55);
        assertThat(policy.recencyWeight(30)).isEqualTo(0.55);
        assertThat(policy.recencyWeight(31)).isZero();

        assertThat(policy.assistanceWeight(ListeningAssistanceLevel.INDEPENDENT))
                .isEqualTo(1.00);
        assertThat(policy.assistanceWeight(ListeningAssistanceLevel.ASSISTED))
                .isEqualTo(0.85);
        assertThat(policy.assistanceWeight(ListeningAssistanceLevel.GUIDED))
                .isZero();

        assertThat(policy.finalWeight(
                6,
                0.8,
                ListeningAssistanceLevel.ASSISTED,
                0.5
        )).isEqualTo(0.85 * 0.8 * 0.85 * 0.5);
    }

    @Test
    void appliesWeakAndRecoveryScoreBoundaries() {
        assertThat(policy.classify(64.999))
                .isEqualTo(ListeningEvidenceBand.WEAK_EVIDENCE);
        assertThat(policy.classify(65))
                .isEqualTo(ListeningEvidenceBand.NEUTRAL);
        assertThat(policy.classify(74.999))
                .isEqualTo(ListeningEvidenceBand.NEUTRAL);
        assertThat(policy.classify(75))
                .isEqualTo(ListeningEvidenceBand.RECOVERY_EVIDENCE);
    }

    @Test
    void requiresThreeDistinctActivitiesAndConfidenceAtLeastPointSeven() {
        var result = policy.aggregate(List.of(
                signal("same", 80, 0.9, 0),
                signal("same", 90, 0.9, 1),
                signal("other", 70, 0.69, 2),
                signal("third", 75, 0.9, 3)
        ));

        assertThat(result.score()).isNull();
        assertThat(result.confidence()).isEqualTo("DATA_COLLECTING");
    }

    @Test
    void growthStartsAtFiveAndMaintainsAtThree() {
        List<ListeningProfilePolicy.Signal> signals = IntStream.range(0, 10)
                .mapToObj(index -> signal(
                        "activity-" + index,
                        index < 5 ? 80 : 75,
                        0.9,
                        index
                )).toList();

        assertThat(policy.growth(signals, false).active()).isTrue();
        assertThat(policy.growth(signals, true).active()).isTrue();
    }

    @Test
    void weaknessUsesRecentFiveEvidence() {
        var signals = List.of(
                signal("a", 60, 0.9, 0),
                signal("b", 64, 0.9, 1),
                signal("c", 70, 0.9, 2)
        );
        assertThat(policy.weakness(signals).state())
                .isEqualTo(ListeningWeaknessState.ACTIVE);
    }

    private ListeningProfilePolicy.Signal signal(
            String activity,
            double score,
            double confidence,
            int daysAgo
    ) {
        return new ListeningProfilePolicy.Signal(
                activity,
                score,
                confidence,
                ListeningAssistanceLevel.INDEPENDENT,
                1,
                true,
                false,
                false,
                LocalDateTime.now().minusDays(daysAgo)
        );
    }
}
