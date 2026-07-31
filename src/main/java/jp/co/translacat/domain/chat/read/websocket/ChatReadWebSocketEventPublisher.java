package jp.co.translacat.domain.chat.read.websocket;

import jp.co.translacat.domain.chat.read.dto.websocket.ChatReadUpdatedEventDto;
import jp.co.translacat.domain.chat.read.event.ChatReadUpdatedApplicationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class ChatReadWebSocketEventPublisher {

    private static final String USER_READ_DESTINATION =
            "/queue/chat/read";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ChatReadUpdatedApplicationEvent event) {
        if (event.destinationUsername() == null
                || event.destinationUsername().isBlank()) {
            return;
        }

        messagingTemplate.convertAndSendToUser(
                event.destinationUsername(),
                USER_READ_DESTINATION,
                ChatReadUpdatedEventDto.from(
                        event.userId(),
                        event.response()
                )
        );
    }
}
