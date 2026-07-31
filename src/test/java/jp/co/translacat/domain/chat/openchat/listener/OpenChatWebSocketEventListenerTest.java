package jp.co.translacat.domain.chat.openchat.listener;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.openchat.event.OpenChatProfileUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatRoomClosedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileImageUrlResolver;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenChatWebSocketEventListenerTest {

    @Mock private ChatWebSocketEventPublisher publisher;
    @Mock private OpenChatProfileImageUrlResolver imageUrlResolver;

    private OpenChatWebSocketEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OpenChatWebSocketEventListener(
                publisher,
                imageUrlResolver
        );
    }

    @Test
    void publishesProfileUpdatedPayload() {
        LocalDateTime occurredAt = LocalDateTime.of(
                2026,
                8,
                1,
                12,
                0
        );
        OpenChatProfileUpdatedApplicationEvent event =
                new OpenChatProfileUpdatedApplicationEvent(
                        100L,
                        20L,
                        "OC-ABCDE",
                        "cat",
                        "open-chat-profiles/20/cat.png",
                        ChatRoomMemberRole.MEMBER,
                        occurredAt
                );
        when(imageUrlResolver.resolve(
                "open-chat-profiles/20/cat.png"
        )).thenReturn("https://cdn.test/cat.png");

        listener.handle(event);

        verify(publisher).publishOpenChatProfileUpdated(
                100L,
                20L,
                "OC-ABCDE",
                "cat",
                "https://cdn.test/cat.png",
                ChatRoomMemberRole.MEMBER,
                occurredAt
        );
    }

    @Test
    void publishesRoomClosedPayload() {
        LocalDateTime closedAt = LocalDateTime.of(
                2026,
                8,
                1,
                13,
                0
        );
        LocalDateTime occurredAt = LocalDateTime.of(
                2026,
                8,
                1,
                13,
                1
        );
        OpenChatRoomClosedApplicationEvent event =
                new OpenChatRoomClosedApplicationEvent(
                        100L,
                        closedAt,
                        occurredAt
                );

        listener.handle(event);

        verify(publisher).publishOpenChatRoomClosed(
                100L,
                closedAt,
                occurredAt
        );
    }
}
