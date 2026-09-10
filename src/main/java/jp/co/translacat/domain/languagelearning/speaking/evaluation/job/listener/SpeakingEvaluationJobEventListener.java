package jp.co.translacat.domain.languagelearning.speaking.evaluation.job.listener;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.event.SpeakingEvaluationJobRequestedEvent;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.SpeakingEvaluationJobDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SpeakingEvaluationJobEventListener {
    private final SpeakingEvaluationJobDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SpeakingEvaluationJobRequestedEvent event) {
        dispatcher.dispatch(event.key());
    }
}
