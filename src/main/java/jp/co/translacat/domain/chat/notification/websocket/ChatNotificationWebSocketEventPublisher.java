package jp.co.translacat.domain.chat.notification.websocket;

import jp.co.translacat.domain.chat.notification.dto.websocket.event.ChatNotificationCreatedEventDto;
import jp.co.translacat.domain.chat.notification.event.ChatNotificationCreatedApplicationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatNotificationWebSocketEventPublisher {

    private static final String USER_NOTIFICATION_DESTINATION =
            "/queue/chat/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ChatNotificationCreatedApplicationEvent event) {
        if (event == null
                || event.destinationUsername() == null
                || event.destinationUsername().isBlank()
                || event.notification() == null) {
            return;
        }

        try {
            messagingTemplate.convertAndSendToUser(
                    event.destinationUsername(),
                    USER_NOTIFICATION_DESTINATION,
                    ChatNotificationCreatedEventDto.from(event)
            );
        } catch (Exception exception) {
            log.error(
                    "Failed to publish chat activity notification websocket event. destinationUsername={}, notificationId={}",
                    event.destinationUsername(),
                    event.notification().id(),
                    exception
            );
        }
    }
}
