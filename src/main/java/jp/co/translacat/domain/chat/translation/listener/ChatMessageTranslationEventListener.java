package jp.co.translacat.domain.chat.translation.listener;

import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationRequestedEvent;
import jp.co.translacat.domain.chat.translation.service.ChatMessageTranslationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageTranslationEventListener {

    private final ChatMessageTranslationProcessor chatMessageTranslationProcessor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageTranslationRequestedEvent event) {
        log.debug(
                "Chat message translation requested event received. messageId={}, translationIds={}",
                event.messageId(),
                event.translationIds()
        );

        chatMessageTranslationProcessor.process(event);
    }
}