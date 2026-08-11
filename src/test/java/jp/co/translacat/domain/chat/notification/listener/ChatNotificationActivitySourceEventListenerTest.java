package jp.co.translacat.domain.chat.notification.listener;

import jp.co.translacat.domain.chat.member.event.ChatRoomMemberInvitedApplicationEvent;
import jp.co.translacat.domain.chat.notification.service.ChatNotificationActivityCreationService;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberBannedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberRoleUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatRoomClosedApplicationEvent;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatNotificationActivitySourceEventListenerTest {

    @Mock
    private ChatNotificationActivityCreationService creationService;

    @Test
    void delegatesAllSupportedSourceEvents() {
        ChatNotificationActivitySourceEventListener listener =
                new ChatNotificationActivitySourceEventListener(
                        creationService
                );
        ChatRoomMemberInvitedApplicationEvent invitation =
                ChatRoomMemberInvitedApplicationEvent.of(
                        1L,
                        2L,
                        3L,
                        4L,
                        LocalDateTime.now()
                );
        OpenChatMemberBannedApplicationEvent banned =
                OpenChatMemberBannedApplicationEvent.of(
                        1L,
                        4L,
                        "target@translacat.test",
                        "reason",
                        LocalDateTime.now()
                );
        OpenChatMemberRoleUpdatedApplicationEvent role =
                OpenChatMemberRoleUpdatedApplicationEvent.of(
                        1L,
                        4L,
                        ChatRoomMemberRole.ADMIN
                );
        OpenChatRoomClosedApplicationEvent closed =
                OpenChatRoomClosedApplicationEvent.of(
                        1L,
                        LocalDateTime.now()
                );

        listener.handle(invitation);
        listener.handle(banned);
        listener.handle(role);
        listener.handle(closed);

        verify(creationService).createInvitation(invitation);
        verify(creationService).createOpenChatKicked(banned);
        verify(creationService).createOpenChatRoleChanged(role);
        verify(creationService).createOpenChatRoomClosed(closed);
    }

    @Test
    void doesNotPropagateNotificationCreationFailureAfterSourceCommit() {
        ChatNotificationActivitySourceEventListener listener =
                new ChatNotificationActivitySourceEventListener(
                        creationService
                );
        ChatRoomMemberInvitedApplicationEvent event =
                ChatRoomMemberInvitedApplicationEvent.of(
                        1L,
                        2L,
                        3L,
                        4L,
                        LocalDateTime.now()
                );
        doThrow(new IllegalStateException("notification failure"))
                .when(creationService)
                .createInvitation(event);

        assertThatCode(() -> listener.handle(event))
                .doesNotThrowAnyException();
    }
}
