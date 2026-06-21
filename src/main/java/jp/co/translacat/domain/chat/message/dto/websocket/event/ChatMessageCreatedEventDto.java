package jp.co.translacat.domain.chat.message.dto.websocket.event;

import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatMessageCreatedEventDto(
        String eventType,
        Long chatRoomId,
        ChatMessageResponseDto message,
        LocalDateTime occurredAt
) {

    public static ChatMessageCreatedEventDto from(
            Long chatRoomId,
            ChatMessageResponseDto message
    ) {
        return new ChatMessageCreatedEventDto(
                ChatWebSocketEventType.MESSAGE_CREATED.getEventName(),
                chatRoomId,
                message,
                LocalDateTime.now()
        );
    }
}