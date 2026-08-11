package jp.co.translacat.domain.chat.notification.websocket;

import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityItemResponseDto;
import jp.co.translacat.domain.chat.notification.dto.websocket.event.ChatNotificationCreatedEventDto;
import jp.co.translacat.domain.chat.notification.enums.ChatNotificationType;
import jp.co.translacat.domain.chat.notification.event.ChatNotificationCreatedApplicationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatNotificationWebSocketEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void publishesCreatedNotificationToUserQueue() {
        ChatNotificationWebSocketEventPublisher publisher =
                new ChatNotificationWebSocketEventPublisher(
                        messagingTemplate
                );
        ChatNotificationActivityItemResponseDto notification =
                new ChatNotificationActivityItemResponseDto(
                        10L,
                        ChatNotificationType.CHAT_INVITATION,
                        100L,
                        Map.of("roomName", "group"),
                        false,
                        null,
                        LocalDateTime.of(2026, 8, 11, 14, 0)
                );
        ChatNotificationCreatedApplicationEvent event =
                ChatNotificationCreatedApplicationEvent.of(
                        "recipient@translacat.test",
                        notification
                );

        publisher.publish(event);

        ArgumentCaptor<Object> payloadCaptor =
                ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("recipient@translacat.test"),
                eq("/queue/chat/notifications"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue())
                .isInstanceOf(ChatNotificationCreatedEventDto.class);
        ChatNotificationCreatedEventDto payload =
                (ChatNotificationCreatedEventDto) payloadCaptor.getValue();
        assertThat(payload.eventType())
                .isEqualTo("chat.notification.created");
        assertThat(payload.notification().id()).isEqualTo(10L);
    }

    @Test
    void websocketFailureDoesNotPropagateAfterNotificationCommit() {
        ChatNotificationWebSocketEventPublisher publisher =
                new ChatNotificationWebSocketEventPublisher(
                        messagingTemplate
                );
        ChatNotificationActivityItemResponseDto notification =
                new ChatNotificationActivityItemResponseDto(
                        10L,
                        ChatNotificationType.CHAT_INVITATION,
                        100L,
                        Map.of(),
                        false,
                        null,
                        LocalDateTime.now()
                );
        ChatNotificationCreatedApplicationEvent event =
                ChatNotificationCreatedApplicationEvent.of(
                        "recipient@translacat.test",
                        notification
                );
        doThrow(new IllegalStateException("websocket failure"))
                .when(messagingTemplate)
                .convertAndSendToUser(
                        eq("recipient@translacat.test"),
                        eq("/queue/chat/notifications"),
                        any()
                );

        assertThatCode(() -> publisher.publish(event))
                .doesNotThrowAnyException();
    }
}
