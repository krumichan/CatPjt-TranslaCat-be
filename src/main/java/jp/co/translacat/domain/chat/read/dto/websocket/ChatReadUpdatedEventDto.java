package jp.co.translacat.domain.chat.read.dto.websocket;

import jp.co.translacat.domain.chat.read.dto.response.ChatRoomReadResponseDto;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatReadUpdatedEventDto(
        String eventType,
        Long chatRoomId,
        Long userId,
        Long lastReadMessageId,
        LocalDateTime lastReadAt,
        long unreadCount,
        LocalDateTime occurredAt
) {
    public static ChatReadUpdatedEventDto from(
            Long userId,
            ChatRoomReadResponseDto response
    ) {
        return new ChatReadUpdatedEventDto(
                ChatWebSocketEventType.READ_UPDATED.getEventName(),
                response.chatRoomId(),
                userId,
                response.lastReadMessageId(),
                response.lastReadAt(),
                response.unreadCount(),
                LocalDateTime.now()
        );
    }
}
