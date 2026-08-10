package jp.co.translacat.domain.chat.websocket.service;

import jp.co.translacat.domain.chat.presence.dto.websocket.event.ChatPresenceChangedEventDto;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketEventPublisherPresenceTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    @Test
    void publishPresenceChanged_UsesRoomScopedSafeMemberContract() {
        ChatWebSocketEventPublisher publisher =
                new ChatWebSocketEventPublisher(messagingTemplate);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 10, 22, 35);

        publisher.publishPresenceChanged(
                10L,
                ChatRoomType.OPEN,
                "3001",
                true,
                occurredAt
        );

        ArgumentCaptor<ChatPresenceChangedEventDto> eventCaptor =
                ArgumentCaptor.forClass(ChatPresenceChangedEventDto.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/rooms/10"),
                eventCaptor.capture()
        );

        ChatPresenceChangedEventDto event = eventCaptor.getValue();
        assertEquals("chat.presence.changed", event.eventType());
        assertEquals(10L, event.roomId());
        assertEquals(ChatRoomType.OPEN, event.roomType());
        assertEquals("3001", event.memberRef());
        assertTrue(event.online());
        assertEquals(occurredAt, event.occurredAt());
    }
}
