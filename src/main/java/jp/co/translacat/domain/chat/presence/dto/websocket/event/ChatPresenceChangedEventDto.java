package jp.co.translacat.domain.chat.presence.dto.websocket.event;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatPresenceChangedEventDto(
        String eventType,
        Long roomId,
        ChatRoomType roomType,
        String memberRef,
        boolean online,
        @ChatUtcTimestamp LocalDateTime occurredAt
) {
    public static ChatPresenceChangedEventDto of(
            Long roomId,
            ChatRoomType roomType,
            String memberRef,
            boolean online,
            LocalDateTime occurredAt
    ) {
        return new ChatPresenceChangedEventDto(
                ChatWebSocketEventType.PRESENCE_CHANGED.getEventName(),
                roomId,
                roomType,
                memberRef,
                online,
                occurredAt
        );
    }
}
