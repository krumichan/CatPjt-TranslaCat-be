package jp.co.translacat.domain.chat.read.dto.websocket;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.read.event.ChatMemberReadUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatMemberReadUpdatedEventDto(
        String eventType,
        Long chatRoomId,
        Long readerUserId,
        Long readerOpenChatMemberId,
        Long previousLastReadMessageId,
        Long lastReadMessageId,
        @ChatUtcTimestamp LocalDateTime readAt,
        @ChatUtcTimestamp LocalDateTime occurredAt
) {
    public static ChatMemberReadUpdatedEventDto from(
            ChatMemberReadUpdatedApplicationEvent event
    ) {
        return new ChatMemberReadUpdatedEventDto(
                ChatWebSocketEventType.MEMBER_READ_UPDATED
                        .getEventName(),
                event.chatRoomId(),
                event.readerUserId(),
                event.readerOpenChatMemberId(),
                event.previousLastReadMessageId(),
                event.lastReadMessageId(),
                event.readAt(),
                LocalDateTime.now()
        );
    }
}
