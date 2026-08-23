package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListeningIdempotencyPolicyTest {

    private final ListeningIdempotencyPolicy policy =
            new ListeningIdempotencyPolicy();

    @Test
    void acceptsSamePayloadRegardlessOfTaskOrder() {
        assertThatCode(() -> policy.requireSamePayload(
                101L,
                List.of(
                        ListeningTaskType.DICTATION,
                        ListeningTaskType.INTERPRETATION
                ),
                101L,
                List.of(
                        ListeningTaskType.INTERPRETATION,
                        ListeningTaskType.DICTATION
                )
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsSameKeyWithDifferentResource() {
        assertConflict(() -> policy.requireSamePayload(
                101L,
                List.of(ListeningTaskType.DICTATION),
                102L,
                List.of(ListeningTaskType.DICTATION)
        ));
    }

    @Test
    void rejectsSameKeyWithDifferentTasks() {
        assertConflict(() -> policy.requireSamePayload(
                101L,
                List.of(ListeningTaskType.DICTATION),
                101L,
                List.of(ListeningTaskType.REPEAT_AFTER_AUDIO)
        ));
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                LanguageLearningErrorCode.LISTENING_IDEMPOTENCY_CONFLICT
                        ));
    }
}
