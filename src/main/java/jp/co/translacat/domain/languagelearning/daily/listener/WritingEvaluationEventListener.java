package jp.co.translacat.domain.languagelearning.daily.listener;

import jp.co.translacat.domain.languagelearning.daily.event.WritingEvaluationRequestedEvent;
import jp.co.translacat.domain.languagelearning.daily.service.WritingEvaluationProcessor;
import jp.co.translacat.domain.languagelearning.daily.service.WritingEvaluationStateCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WritingEvaluationEventListener {

    private final WritingEvaluationProcessor evaluationProcessor;
    private final WritingEvaluationStateCommandService evaluationStateCommandService;

    @Async("writingEvaluationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(WritingEvaluationRequestedEvent event) {
        try {
            evaluationProcessor.process(event.answerId());
        } catch (Exception e) {
            try {
                evaluationStateCommandService.failIfPending(event.answerId(), e);
            } catch (Exception recoveryException) {
                log.error(
                        "Failed to persist Daily Writing evaluation failure. answerId={}",
                        event.answerId(),
                        recoveryException
                );
            }
            log.error(
                    "Daily Writing evaluation failed. answerId={}",
                    event.answerId(),
                    e
            );
        }
    }
}
