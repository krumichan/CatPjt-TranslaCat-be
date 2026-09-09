package jp.co.translacat.domain.languagelearning.daily.listener;

import jp.co.translacat.domain.languagelearning.daily.event.WritingEvaluationRequestedEvent;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingCompletionCommandService;
import jp.co.translacat.domain.languagelearning.daily.service.WritingEvaluationProcessor;
import jp.co.translacat.domain.languagelearning.daily.service.WritingEvaluationStateCommandService;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class WritingEvaluationEventListenerTest {

    private final WritingEvaluationProcessor processor = mock(WritingEvaluationProcessor.class);
    private final WritingEvaluationStateCommandService evaluationState = mock(WritingEvaluationStateCommandService.class);
    private final DailyWritingCompletionCommandService completion = mock(DailyWritingCompletionCommandService.class);
    private final WritingEvaluationEventListener listener =
            new WritingEvaluationEventListener(processor, evaluationState, completion);

    @Test
    void checksCompletionOnlyAfterTransactionalProcessorReturnsCommittedResult() {
        when(processor.process(21L)).thenReturn(11L);

        listener.handle(new WritingEvaluationRequestedEvent(21L));

        var ordered = inOrder(processor, completion);
        ordered.verify(processor).process(21L);
        ordered.verify(completion).completeIfAllEvaluated(11L);
        verifyNoInteractions(evaluationState);
    }

    @Test
    void failedEvaluationDoesNotRunCompletionBeforeFailureIsPersisted() {
        RuntimeException failure = new IllegalStateException("evaluation failed");
        when(processor.process(21L)).thenThrow(failure);

        listener.handle(new WritingEvaluationRequestedEvent(21L));

        verify(evaluationState).failIfPending(21L, failure);
        verifyNoInteractions(completion);
    }

    @Test
    void absentSuccessfulEvaluationDoesNotRunCompletion() {
        when(processor.process(21L)).thenReturn(null);

        listener.handle(new WritingEvaluationRequestedEvent(21L));

        verifyNoInteractions(completion, evaluationState);
    }

    @Test
    void completionFailureNeverReclassifiesAlreadyCommittedEvaluation() {
        when(processor.process(21L)).thenReturn(11L);
        doThrow(new IllegalStateException("lock timeout")).when(completion).completeIfAllEvaluated(11L);

        assertThatCode(() -> listener.handle(new WritingEvaluationRequestedEvent(21L)))
                .doesNotThrowAnyException();

        verifyNoInteractions(evaluationState);
    }

    @Test
    void completionAlwaysUsesFreshTransactionAfterEvaluationCommit() throws Exception {
        Transactional evaluationTransaction = WritingEvaluationProcessor.class
                .getMethod("process", Long.class).getAnnotation(Transactional.class);
        Transactional completionTransaction = DailyWritingCompletionCommandService.class
                .getMethod("completeIfAllEvaluated", Long.class).getAnnotation(Transactional.class);

        assertThat(evaluationTransaction).isNotNull();
        assertThat(evaluationTransaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(completionTransaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(completionTransaction.readOnly()).isFalse();
    }
}
