package jp.co.translacat.domain.chat.websocket.service;

import jp.co.translacat.domain.chat.presence.dto.websocket.event.ChatPresenceChangedEventDto;
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
    void publishPresenceChanged_UsesExistingRoomTopicContract() {
        ChatWebSocketEventPublisher publisher =
                new ChatWebSocketEventPublisher(messagingTemplate);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 10, 19, 40);

        publisher.publishPresenceChanged(
                10L,
                100L,
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
        assertEquals(100L, event.userId());
        assertTrue(event.online());
        assertEquals(occurredAt, event.occurredAt());
    }
}
