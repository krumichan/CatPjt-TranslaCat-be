package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import jp.co.translacat.domain.languagelearning.resultjournal.model.LearningResultCaptured;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

public class ResultJournalListener {
    private final ResultOutboxStore store;
    private final ResultDeliveryProperties properties;
    public ResultJournalListener(ResultOutboxStore store, ResultDeliveryProperties properties) {
        this.store = store; this.properties = properties;
    }
    // AFTER_COMMIT로 바꾸면 평가 성공과 outbox 기록 사이의 프로세스 종료로 결과를 잃을 수 있다.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void capture(LearningResultCaptured result) {
        if (properties.isEnabled()) store.append(properties.getSourceInstanceId(), result);
    }
}
