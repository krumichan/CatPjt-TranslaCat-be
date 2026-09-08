package jp.co.translacat.batch.languagelearning.listening;

import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListeningOutboxBatch {
    private final ListeningOutboxDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${language-learning.listening.outbox-delay-ms:1000}")
    public void dispatch() {
        dispatcher.dispatch();
    }
}
