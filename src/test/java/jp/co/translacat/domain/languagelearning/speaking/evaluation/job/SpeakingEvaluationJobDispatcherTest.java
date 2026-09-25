package jp.co.translacat.domain.languagelearning.speaking.evaluation.job;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model.SpeakingEvaluationClaim;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model.SpeakingEvaluationJobKey;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.SpeakingEvaluationJobCommandService;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.SpeakingEvaluationJobDispatcher;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.SpeakingEvaluationJobWorker;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static jp.co.translacat.domain.languagelearning.speaking.evaluation.release.SpeakingReleaseFixtures.request;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class SpeakingEvaluationJobDispatcherTest {
    @Test
    void releasesClaimWhenTheWorkerPoolIsFull() {
        var command = mock(SpeakingEvaluationJobCommandService.class);
        var worker = mock(SpeakingEvaluationJobWorker.class);
        var key = new SpeakingEvaluationJobKey(1L, 2L);
        var claim = new SpeakingEvaluationClaim(key, 0, "token", 0, request("evaluated"));
        when(command.claim(key)).thenReturn(Optional.of(claim));
        var dispatcher = new SpeakingEvaluationJobDispatcher(command, worker, task -> {
            throw new RejectedExecutionException();
        });
        assertThatCode(() -> dispatcher.dispatch(key)).doesNotThrowAnyException();
        verify(command).release(claim);
        verifyNoInteractions(worker);
    }

    @Test
    void committedSubmissionDoesNotBecomeHttpFailureWhenClaimingIsTemporarilyUnavailable() {
        var command = mock(SpeakingEvaluationJobCommandService.class);
        var worker = mock(SpeakingEvaluationJobWorker.class);
        var key = new SpeakingEvaluationJobKey(1L, 2L);
        when(command.claim(key)).thenThrow(new IllegalStateException("temporary database outage"));
        assertThatCode(() -> new SpeakingEvaluationJobDispatcher(command, worker, Runnable::run).dispatch(key))
                .doesNotThrowAnyException();
        verifyNoInteractions(worker);
    }
}
