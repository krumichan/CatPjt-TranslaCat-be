package jp.co.translacat.domain.languagelearning.speaking.evaluation.listener;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.event.SpeakingEvaluationRequestedEvent;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.service.SpeakingEvaluationProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpeakingEvaluationEventListener {

    private final SpeakingEvaluationProcessor evaluationProcessor;

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SpeakingEvaluationRequestedEvent event) {
        try {
            evaluationProcessor.process(
                    event.sessionId(),
                    event.manualRetryAttempt()
            );
        } catch (Exception e) {
            log.error(
                    "Speaking evaluation failed. sessionId={}",
                    event.sessionId(),
                    e
            );
        }
    }
}
