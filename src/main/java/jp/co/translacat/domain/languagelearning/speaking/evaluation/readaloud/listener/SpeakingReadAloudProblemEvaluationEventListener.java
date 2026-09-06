package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.listener;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.event.SpeakingReadAloudProblemEvaluationRequestedEvent;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.service.SpeakingReadAloudProblemEvaluationProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpeakingReadAloudProblemEvaluationEventListener {

    private final SpeakingReadAloudProblemEvaluationProcessor processor;

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SpeakingReadAloudProblemEvaluationRequestedEvent event) {
        try {
            processor.process(event.sessionId(), event.problemIndex());
        } catch (Exception e) {
            log.error(
                    "Read Aloud problem evaluation failed. sessionId={} problemIndex={}",
                    event.sessionId(),
                    event.problemIndex(),
                    e
            );
        }
    }
}
