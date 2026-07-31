package jp.co.translacat.domain.chat.openchat.dto.websocket.event;

import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record OpenChatRoomClosedEventDto(
        String eventType,
        Long roomId,
        LocalDateTime closedAt,
        LocalDateTime occurredAt
) {

    public static OpenChatRoomClosedEventDto of(
            Long roomId,
            LocalDateTime closedAt,
            LocalDateTime occurredAt
    ) {
        return new OpenChatRoomClosedEventDto(
                ChatWebSocketEventType.ROOM_CLOSED.getEventName(),
                roomId,
                closedAt,
                occurredAt
        );
    }
}
