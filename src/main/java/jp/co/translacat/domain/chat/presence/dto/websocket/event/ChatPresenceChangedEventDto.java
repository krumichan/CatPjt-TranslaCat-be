package jp.co.translacat.domain.chat.presence.dto.websocket.event;

import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatPresenceChangedEventDto(
        String eventType,
        Long roomId,
        Long userId,
        boolean online,
        LocalDateTime occurredAt
) {
    public static ChatPresenceChangedEventDto of(
            Long roomId,
            Long userId,
            boolean online,
            LocalDateTime occurredAt
    ) {
        return new ChatPresenceChangedEventDto(
                ChatWebSocketEventType.PRESENCE_CHANGED.getEventName(),
                roomId,
                userId,
                online,
                occurredAt
        );
    }
}
