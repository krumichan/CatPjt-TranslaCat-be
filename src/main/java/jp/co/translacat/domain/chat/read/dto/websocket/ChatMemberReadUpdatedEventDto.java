package jp.co.translacat.domain.chat.read.dto.websocket;

import jp.co.translacat.domain.chat.read.event.ChatMemberReadUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatMemberReadUpdatedEventDto(
        String eventType,
        Long chatRoomId,
        Long readerUserId,
        Long previousLastReadMessageId,
        Long lastReadMessageId,
        LocalDateTime readAt,
        LocalDateTime occurredAt
) {
    public static ChatMemberReadUpdatedEventDto from(
            ChatMemberReadUpdatedApplicationEvent event
    ) {
        return new ChatMemberReadUpdatedEventDto(
                ChatWebSocketEventType.MEMBER_READ_UPDATED
                        .getEventName(),
                event.chatRoomId(),
                event.readerUserId(),
                event.previousLastReadMessageId(),
                event.lastReadMessageId(),
                event.readAt(),
                LocalDateTime.now()
        );
    }
}
