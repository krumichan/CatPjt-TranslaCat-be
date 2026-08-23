package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListeningTaskSelectionPolicyTest {

    private final ListeningTaskSelectionPolicy policy =
            new ListeningTaskSelectionPolicy();

    @Test
    void allowsExactlySixTaskCombinations() {
        List<List<ListeningTaskType>> allowed = List.of(
                List.of(ListeningTaskType.DICTATION),
                List.of(ListeningTaskType.REPEAT_AFTER_AUDIO),
                List.of(ListeningTaskType.DICTATION,
                        ListeningTaskType.INTERPRETATION),
                List.of(ListeningTaskType.DICTATION,
                        ListeningTaskType.REPEAT_AFTER_AUDIO),
                List.of(ListeningTaskType.INTERPRETATION,
                        ListeningTaskType.REPEAT_AFTER_AUDIO),
                List.of(ListeningTaskType.DICTATION,
                        ListeningTaskType.INTERPRETATION,
                        ListeningTaskType.REPEAT_AFTER_AUDIO)
        );

        assertThat(allowed).allMatch(policy::isAllowed);
        assertThat(allowed).hasSize(6);
    }

    @Test
    void rejectsInterpretationOnlyEmptyAndDuplicates() {
        assertThatThrownBy(() -> policy.validate(
                List.of(ListeningTaskType.INTERPRETATION)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.validate(List.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.validate(List.of(
                ListeningTaskType.DICTATION,
                ListeningTaskType.DICTATION
        ))).isInstanceOf(BusinessException.class);
    }
}
