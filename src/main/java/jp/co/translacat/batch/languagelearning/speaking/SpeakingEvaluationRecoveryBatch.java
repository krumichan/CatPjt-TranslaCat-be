package jp.co.translacat.batch.languagelearning.speaking;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.SpeakingEvaluationJobDispatcher;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.SpeakingEvaluationJobQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpeakingEvaluationRecoveryBatch {
    private final SpeakingEvaluationJobQueryService queryService;
    private final SpeakingEvaluationJobDispatcher dispatcher;

    @Scheduled(initialDelayString = "${language-learning.speaking.evaluation-job.recovery-delay-ms:10000}",
            fixedDelayString = "${language-learning.speaking.evaluation-job.recovery-delay-ms:10000}")
    public void recover() {
        try {
            queryService.findDue(20).forEach(dispatcher::dispatch);
        } catch (RuntimeException failure) {
            log.error("Speaking evaluation recovery scan failed; it will be retried.", failure);
        }
    }
}
