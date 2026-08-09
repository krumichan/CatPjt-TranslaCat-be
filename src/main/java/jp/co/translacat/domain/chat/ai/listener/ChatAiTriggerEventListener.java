package jp.co.translacat.domain.chat.ai.listener;

import jp.co.translacat.domain.chat.ai.event.ChatAiTriggerRequestedEvent;
import jp.co.translacat.domain.chat.ai.service.ChatAiTriggerProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAiTriggerEventListener {

    private final ChatAiTriggerProcessor triggerProcessor;

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatAiTriggerRequestedEvent event) {
        if (event == null || event.messageId() == null) {
            return;
        }
        log.debug(
                "AI trigger requested event received. messageId={}",
                event.messageId()
        );
        triggerProcessor.process(event.messageId());
    }
}
