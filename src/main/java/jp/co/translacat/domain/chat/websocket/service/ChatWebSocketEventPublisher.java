package jp.co.translacat.domain.chat.websocket.service;

import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.dto.websocket.event.ChatMessageCreatedEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatWebSocketEventPublisher {

    private static final String CHAT_ROOM_TOPIC_PREFIX = "/topic/chat/rooms/";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishMessageCreated(
            Long chatRoomId,
            ChatMessageResponseDto message
    ) {
        ChatMessageCreatedEventDto event =
                ChatMessageCreatedEventDto.from(
                        chatRoomId,
                        message
                );

        messagingTemplate.convertAndSend(
                CHAT_ROOM_TOPIC_PREFIX + chatRoomId,
                event
        );
    }
}