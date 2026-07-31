package jp.co.translacat.domain.chat.websocket.service;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.dto.websocket.event.ChatMessageCreatedEventDto;
import jp.co.translacat.domain.chat.openchat.dto.websocket.event.OpenChatProfileUpdatedEventDto;
import jp.co.translacat.domain.chat.openchat.dto.websocket.event.OpenChatRoomClosedEventDto;
import jp.co.translacat.domain.chat.translation.dto.websocket.event.ChatTranslationCompletedEventDto;
import jp.co.translacat.domain.chat.translation.dto.websocket.event.ChatTranslationFailedEventDto;
import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationCompletedEvent;
import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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

        publishToRoom(chatRoomId, event);
    }

    public void publishTranslationCompleted(
            ChatMessageTranslationCompletedEvent event
    ) {
        ChatTranslationCompletedEventDto eventDto =
                ChatTranslationCompletedEventDto.from(event);

        publishToRoom(event.chatRoomId(), eventDto);
    }

    public void publishTranslationFailed(
            ChatMessageTranslationFailedEvent event
    ) {
        ChatTranslationFailedEventDto eventDto =
                ChatTranslationFailedEventDto.from(event);

        publishToRoom(event.chatRoomId(), eventDto);
    }

    public void publishOpenChatProfileUpdated(
            Long roomId,
            Long openChatMemberId,
            String memberCode,
            String nickname,
            String profileImageUrl,
            ChatRoomMemberRole role,
            LocalDateTime occurredAt
    ) {
        publishToRoom(
                roomId,
                OpenChatProfileUpdatedEventDto.of(
                        roomId,
                        openChatMemberId,
                        memberCode,
                        nickname,
                        profileImageUrl,
                        role,
                        occurredAt
                )
        );
    }

    public void publishOpenChatRoomClosed(
            Long roomId,
            LocalDateTime closedAt,
            LocalDateTime occurredAt
    ) {
        publishToRoom(
                roomId,
                OpenChatRoomClosedEventDto.of(
                        roomId,
                        closedAt,
                        occurredAt
                )
        );
    }

    private void publishToRoom(Long chatRoomId, Object event) {
        messagingTemplate.convertAndSend(
                CHAT_ROOM_TOPIC_PREFIX + chatRoomId,
                event
        );
    }
}
