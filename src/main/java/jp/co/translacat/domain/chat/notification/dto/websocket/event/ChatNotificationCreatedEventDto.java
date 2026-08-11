package jp.co.translacat.domain.chat.notification.dto.websocket.event;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityItemResponseDto;
import jp.co.translacat.domain.chat.notification.event.ChatNotificationCreatedApplicationEvent;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatNotificationCreatedEventDto(
        String eventType,
        ChatNotificationActivityItemResponseDto notification,
        @ChatUtcTimestamp LocalDateTime occurredAt
) {

    public static ChatNotificationCreatedEventDto from(
            ChatNotificationCreatedApplicationEvent event
    ) {
        return new ChatNotificationCreatedEventDto(
                ChatWebSocketEventType.NOTIFICATION_CREATED.getEventName(),
                event.notification(),
                event.occurredAt()
        );
    }
}
