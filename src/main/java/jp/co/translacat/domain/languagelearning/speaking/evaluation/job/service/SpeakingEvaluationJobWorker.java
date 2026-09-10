package jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service;

import jp.co.translacat.domain.languagelearning.speaking.ai.port.SpeakingAiClient;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model.SpeakingEvaluationClaim;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingEvaluationJobWorker {
    private final SpeakingAiClient aiClient;
    private final SpeakingEvaluationJobCommandService commandService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void execute(SpeakingEvaluationClaim claim) {
        try {
            var response = aiClient.evaluate(claim.request());
            commandService.complete(claim, response);
        } catch (RuntimeException failure) {
            log.error("Speaking evaluation failed. jobId={} sessionId={} problemIndex={}",
                    claim.key().jobId(), claim.key().sessionId(), claim.problemIndex(), failure);
            try {
                commandService.fail(claim);
            } catch (RuntimeException persistenceFailure) {
                // Leave the RUNNING lease recoverable; never acknowledge work that was not persisted.
                log.error("Speaking evaluation failure state could not be saved. jobId={}",
                        claim.key().jobId(), persistenceFailure);
            }
        }
    }
}
