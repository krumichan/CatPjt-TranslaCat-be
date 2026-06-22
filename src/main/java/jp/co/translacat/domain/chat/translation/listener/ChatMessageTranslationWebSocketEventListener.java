package jp.co.translacat.domain.chat.translation.listener;

import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationCompletedEvent;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageTranslationWebSocketEventListener {

    private final ChatWebSocketEventPublisher chatWebSocketEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageTranslationCompletedEvent event) {
        log.debug(
                "Publish chat translation completed event. messageId={}, translationId={}, languageCode={}",
                event.messageId(),
                event.translationId(),
                event.languageCode()
        );

        chatWebSocketEventPublisher.publishTranslationCompleted(event);
    }
}