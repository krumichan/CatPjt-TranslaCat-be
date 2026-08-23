package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningMetricType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListeningEvaluationContractPolicyTest {

    private final ListeningEvaluationContractPolicy policy =
            new ListeningEvaluationContractPolicy();

    @Test
    void acceptsFixedDictationMetricWeights() {
        policy.validateMetrics(ListeningTaskType.DICTATION, List.of(
                new ListeningEvaluationContractPolicy.MetricProjection(
                        ListeningMetricType.TOKEN_RECOGNITION, 0.60),
                new ListeningEvaluationContractPolicy.MetricProjection(
                        ListeningMetricType.OMISSION_ADDITION_ORDER, 0.25),
                new ListeningEvaluationContractPolicy.MetricProjection(
                        ListeningMetricType.ORTHOGRAPHY, 0.15)
        ));
    }

    @Test
    void rejectsUnknownOrChangedWeights() {
        assertThatThrownBy(() -> policy.validateMetrics(
                ListeningTaskType.DICTATION,
                List.of(
                        new ListeningEvaluationContractPolicy.MetricProjection(
                                ListeningMetricType.TOKEN_RECOGNITION, 0.50),
                        new ListeningEvaluationContractPolicy.MetricProjection(
                                ListeningMetricType.OMISSION_ADDITION_ORDER, 0.35),
                        new ListeningEvaluationContractPolicy.MetricProjection(
                                ListeningMetricType.ORTHOGRAPHY, 0.15)
                )
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void profileEligibilityRequiresOfficialIndependentEvidence() {
        assertThat(policy.profileEligible(
                true, false, false, false, true,
                75.0, 0.70, 1.0
        )).isTrue();
        assertThat(policy.profileEligible(
                true, false, true, false, true,
                75.0, 0.90, 1.0
        )).isFalse();
        assertThat(policy.profileEligible(
                false, true, false, false, true,
                90.0, 0.90, 1.0
        )).isFalse();
    }

    @Test
    void rejectsDuplicateProfileSignals() {
        var signal = new ListeningEvaluationContractPolicy.ProfileSignalProjection(
                ListeningProfileMetric.LISTENING_RECOGNITION,
                1.0
        );

        assertThatThrownBy(() -> policy.validateProfileSignals(
                ListeningTaskType.DICTATION,
                List.of(signal, signal)
        )).isInstanceOf(BusinessException.class);
    }
}
