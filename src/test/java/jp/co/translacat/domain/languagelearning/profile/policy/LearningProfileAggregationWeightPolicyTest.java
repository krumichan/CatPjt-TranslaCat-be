package jp.co.translacat.domain.languagelearning.profile.policy;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LearningProfileAggregationWeightPolicyTest {

    private final LearningProfileAggregationWeightPolicy policy =
            new LearningProfileAggregationWeightPolicy();

    @Test
    void recentActivityHasMoreWeightThanOldActivity() {
        assertThat(policy.recencyWeight(0, 30))
                .isGreaterThan(policy.recencyWeight(29, 30));
        assertThat(policy.recencyWeight(0, 30)).isEqualTo(1.0);
        assertThat(policy.recencyWeight(29, 30)).isEqualTo(0.5);
    }

    @Test
    void sampleAnswerDoesNotReducePronunciationOrFluency() {
        double pronunciation = policy.speakingActivityWeight(
                0,
                1,
                1.0,
                1.0,
                List.of(AssistanceType.SAMPLE_ANSWER),
                SpeakingMetricType.PRONUNCIATION
        );
        double grammar = policy.speakingActivityWeight(
                0,
                1,
                1.0,
                1.0,
                List.of(AssistanceType.SAMPLE_ANSWER),
                SpeakingMetricType.GRAMMAR
        );

        assertThat(pronunciation).isEqualTo(1.0);
        assertThat(grammar).isEqualTo(0.6);
    }

    @Test
    void unifiedPatternNeedsTwoSourcesAndThreeEvidence() {
        assertThat(policy.isUnified(2, 2)).isFalse();
        assertThat(policy.isUnified(2, 3)).isTrue();
        assertThat(policy.isSourceEstablished(1)).isFalse();
        assertThat(policy.isSourceEstablished(2)).isTrue();
    }
}
