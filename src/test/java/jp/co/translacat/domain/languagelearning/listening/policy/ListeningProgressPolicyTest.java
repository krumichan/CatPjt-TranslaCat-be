package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskStatus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListeningProgressPolicyTest {

    private final ListeningProgressPolicy policy = new ListeningProgressPolicy();

    @Test
    void appliesProgressForOfficialEvaluatedAndSystemNotEvaluableLearning() {
        assertThat(policy.eligible(
                true,
                false,
                false,
                false,
                List.of(
                        ListeningTaskStatus.EVALUATED,
                        ListeningTaskStatus.NOT_EVALUABLE
                )
        )).isTrue();
    }

    @Test
    void excludesPracticeAnswerRevealSkipAndDuplicateApplication() {
        assertThat(policy.eligible(
                false, true, false, false,
                List.of(ListeningTaskStatus.EVALUATED)
        )).isFalse();
        assertThat(policy.eligible(
                true, false, true, false,
                List.of(ListeningTaskStatus.NOT_EVALUABLE)
        )).isFalse();
        assertThat(policy.eligible(
                true, false, false, false,
                List.of(ListeningTaskStatus.SKIPPED)
        )).isFalse();
        assertThat(policy.eligible(
                true, false, false, true,
                List.of(ListeningTaskStatus.EVALUATED)
        )).isFalse();
    }

    @Test
    void excludesEmptyOrNonTerminalTaskSets() {
        assertThat(policy.eligible(
                true, false, false, false, List.of()
        )).isFalse();
        assertThat(policy.eligible(
                true, false, false, false,
                List.of(ListeningTaskStatus.EVALUATING)
        )).isFalse();
    }
}
