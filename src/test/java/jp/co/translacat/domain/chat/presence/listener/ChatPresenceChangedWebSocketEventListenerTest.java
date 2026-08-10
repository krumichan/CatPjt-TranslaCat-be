package jp.co.translacat.domain.chat.presence.listener;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPresenceChangedWebSocketEventListenerTest {

    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private ChatWebSocketEventPublisher eventPublisher;

    @Test
    void handle_PublishesPresenceToEveryActiveRoomOfUser() {
        ChatRoom roomA = mock(ChatRoom.class);
        ChatRoom roomB = mock(ChatRoom.class);
        when(roomA.getId()).thenReturn(10L);
        when(roomB.getId()).thenReturn(20L);

        ChatRoomMember memberA = mock(ChatRoomMember.class);
        ChatRoomMember memberB = mock(ChatRoomMember.class);
        when(memberA.getChatRoom()).thenReturn(roomA);
        when(memberB.getChatRoom()).thenReturn(roomB);
        when(chatRoomMemberRepository.findByUserIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(List.of(memberA, memberB));

        ChatPresenceChangedWebSocketEventListener listener =
                new ChatPresenceChangedWebSocketEventListener(
                        chatRoomMemberRepository,
                        eventPublisher
                );
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 10, 19, 35);

        listener.handle(new ChatPresenceChangedApplicationEvent(
                100L,
                true,
                occurredAt
        ));

        verify(eventPublisher).publishPresenceChanged(
                10L,
                100L,
                true,
                occurredAt
        );
        verify(eventPublisher).publishPresenceChanged(
                20L,
                100L,
                true,
                occurredAt
        );
    }
}
