package jp.co.translacat.domain.chat.read.websocket;

import jp.co.translacat.domain.chat.read.dto.response.ChatRoomReadResponseDto;
import jp.co.translacat.domain.chat.read.dto.websocket.ChatMemberReadUpdatedEventDto;
import jp.co.translacat.domain.chat.read.dto.websocket.ChatReadUpdatedEventDto;
import jp.co.translacat.domain.chat.read.event.ChatMemberReadUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.read.event.ChatReadUpdatedApplicationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatReadWebSocketEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatReadWebSocketEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ChatReadWebSocketEventPublisher(
                messagingTemplate
        );
    }

    @Test
    void publishesUserReadEventToUserQueue() {
        LocalDateTime readAt = LocalDateTime.now();
        ChatRoomReadResponseDto response =
                new ChatRoomReadResponseDto(
                        10L,
                        100L,
                        readAt,
                        0L
                );

        publisher.publish(
                ChatReadUpdatedApplicationEvent.of(
                        "reader@translacat.test",
                        1L,
                        response
                )
        );

        ArgumentCaptor<Object> payloadCaptor =
                ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq(
                        "reader@translacat.test"
                ),
                org.mockito.ArgumentMatchers.eq("/queue/chat/read"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue())
                .isInstanceOf(ChatReadUpdatedEventDto.class);
    }

    @Test
    void publishesMemberReadEventToRoomTopic() {
        LocalDateTime readAt = LocalDateTime.now();

        publisher.publish(
                ChatMemberReadUpdatedApplicationEvent.of(
                        10L,
                        1L,
                        90L,
                        100L,
                        readAt
                )
        );

        ArgumentCaptor<Object> payloadCaptor =
                ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(
                        "/topic/chat/rooms/10"
                ),
                payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue())
                .isInstanceOf(ChatMemberReadUpdatedEventDto.class);
        ChatMemberReadUpdatedEventDto payload =
                (ChatMemberReadUpdatedEventDto) payloadCaptor.getValue();
        assertThat(payload.eventType())
                .isEqualTo("chat.member.read.updated");
        assertThat(payload.previousLastReadMessageId())
                .isEqualTo(90L);
        assertThat(payload.lastReadMessageId())
                .isEqualTo(100L);
    }
}
