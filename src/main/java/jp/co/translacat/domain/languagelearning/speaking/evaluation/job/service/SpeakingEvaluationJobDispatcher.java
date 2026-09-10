package jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model.SpeakingEvaluationJobKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class SpeakingEvaluationJobDispatcher {
    private final SpeakingEvaluationJobCommandService commandService;
    private final SpeakingEvaluationJobWorker worker;
    private final Executor executor;

    public SpeakingEvaluationJobDispatcher(SpeakingEvaluationJobCommandService commandService,
            SpeakingEvaluationJobWorker worker, @Qualifier("speakingEvaluationExecutor") Executor executor) {
        this.commandService = commandService;
        this.worker = worker;
        this.executor = executor;
    }

    public void dispatch(SpeakingEvaluationJobKey key) {
        try {
            var claim = commandService.claim(key);
            if (claim.isEmpty()) return;
            try {
                executor.execute(() -> worker.execute(claim.get()));
            } catch (RejectedExecutionException busy) {
                // Queue capacity is deliberately zero: leases are never spent waiting in an executor queue.
                commandService.release(claim.get());
            }
        } catch (RuntimeException failure) {
            // An AFTER_COMMIT listener must not turn a committed submission into a misleading HTTP failure.
            // The persisted PENDING intent or expired RUNNING lease will be found by the recovery batch.
            log.error("Speaking evaluation dispatch deferred. jobId={}", key.jobId(), failure);
        }
    }
}
