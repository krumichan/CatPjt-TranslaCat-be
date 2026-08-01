package jp.co.translacat.domain.chat.websocket.service;

import jp.co.translacat.domain.chat.openchat.dto.websocket.event.OpenChatMemberBannedEventDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketEventPublisherOpenChatBanTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    @Test
    void publishesBanEventToRoomAndTargetUserPrivateQueue() {
        ChatWebSocketEventPublisher publisher =
                new ChatWebSocketEventPublisher(messagingTemplate);
        LocalDateTime bannedAt = LocalDateTime.of(
                2026, 8, 1, 12, 0
        );
        LocalDateTime occurredAt = LocalDateTime.of(
                2026, 8, 1, 12, 0, 1
        );

        publisher.publishOpenChatMemberBanned(
                100L,
                481L,
                "target@open.test",
                "반복적인 도배",
                bannedAt,
                occurredAt
        );

        ArgumentCaptor<OpenChatMemberBannedEventDto> roomEventCaptor =
                ArgumentCaptor.forClass(
                        OpenChatMemberBannedEventDto.class
                );
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/rooms/100"),
                roomEventCaptor.capture()
        );
        assertThat(roomEventCaptor.getValue().eventType())
                .isEqualTo("chat.member.banned");
        assertThat(roomEventCaptor.getValue().targetOpenChatMemberId())
                .isEqualTo(481L);

        ArgumentCaptor<OpenChatMemberBannedEventDto> userEventCaptor =
                ArgumentCaptor.forClass(
                        OpenChatMemberBannedEventDto.class
                );
        verify(messagingTemplate).convertAndSendToUser(
                eq("target@open.test"),
                eq("/queue/chat/open-rooms/100"),
                userEventCaptor.capture()
        );
        assertThat(userEventCaptor.getValue())
                .isEqualTo(roomEventCaptor.getValue());
    }
}
