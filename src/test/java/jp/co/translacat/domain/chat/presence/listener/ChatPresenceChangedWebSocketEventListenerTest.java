package jp.co.translacat.domain.chat.presence.listener;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceVisibilityPolicy;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import jp.co.translacat.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPresenceChangedWebSocketEventListenerTest {

    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private ChatWebSocketEventPublisher eventPublisher;
    @Mock private ChatPresenceVisibilityPolicy visibilityPolicy;

    @Test
    void handle_UsesSafeMemberRefsAndSkipsPrivateAiRoom() {
        ChatRoom groupRoom = mock(ChatRoom.class);
        when(groupRoom.getId()).thenReturn(10L);
        when(groupRoom.getRoomType()).thenReturn(ChatRoomType.GROUP);
        ChatRoomMember groupMember = mock(ChatRoomMember.class);
        when(groupMember.getChatRoom()).thenReturn(groupRoom);
        when(groupMember.getId()).thenReturn(1001L);

        ChatRoom directRoom = mock(ChatRoom.class);
        when(directRoom.getId()).thenReturn(20L);
        when(directRoom.getRoomType()).thenReturn(ChatRoomType.DIRECT);
        User directUser = mock(User.class);
        when(directUser.getPublicId()).thenReturn("TC-DIRECT-USER");
        ChatRoomMember directMember = mock(ChatRoomMember.class);
        when(directMember.getChatRoom()).thenReturn(directRoom);
        when(directMember.getUser()).thenReturn(directUser);

        ChatRoom privateRoom = mock(ChatRoom.class);
        when(privateRoom.getId()).thenReturn(30L);
        when(privateRoom.getRoomType()).thenReturn(ChatRoomType.OPEN);
        ChatRoomMember privateMember = mock(ChatRoomMember.class);
        when(privateMember.getChatRoom()).thenReturn(privateRoom);

        when(chatRoomMemberRepository.findByUserIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(List.of(groupMember, directMember, privateMember));
        when(visibilityPolicy.isVisible(10L)).thenReturn(true);
        when(visibilityPolicy.isVisible(20L)).thenReturn(true);
        when(visibilityPolicy.isVisible(30L)).thenReturn(false);

        ChatPresenceChangedWebSocketEventListener listener =
                new ChatPresenceChangedWebSocketEventListener(
                        chatRoomMemberRepository,
                        eventPublisher,
                        visibilityPolicy
                );
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 10, 22, 30);

        listener.handle(new ChatPresenceChangedApplicationEvent(
                100L,
                true,
                occurredAt
        ));

        verify(eventPublisher).publishPresenceChanged(
                10L,
                ChatRoomType.GROUP,
                "1001",
                true,
                occurredAt
        );
        verify(eventPublisher).publishPresenceChanged(
                20L,
                ChatRoomType.DIRECT,
                "TC-DIRECT-USER",
                true,
                occurredAt
        );
        verify(eventPublisher, never()).publishPresenceChanged(
                30L,
                ChatRoomType.OPEN,
                "3001",
                true,
                occurredAt
        );
    }
}
