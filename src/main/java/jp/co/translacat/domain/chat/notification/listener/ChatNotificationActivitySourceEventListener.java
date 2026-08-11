package jp.co.translacat.domain.chat.notification.listener;

import jp.co.translacat.domain.chat.member.event.ChatRoomMemberInvitedApplicationEvent;
import jp.co.translacat.domain.chat.notification.service.ChatNotificationActivityCreationService;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberBannedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberRoleUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatRoomClosedApplicationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatNotificationActivitySourceEventListener {

    private final ChatNotificationActivityCreationService creationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatRoomMemberInvitedApplicationEvent event) {
        runSafely(
                "CHAT_INVITATION",
                event.roomId(),
                () -> creationService.createInvitation(event)
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OpenChatMemberBannedApplicationEvent event) {
        runSafely(
                "OPEN_CHAT_KICKED",
                event.roomId(),
                () -> creationService.createOpenChatKicked(event)
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OpenChatMemberRoleUpdatedApplicationEvent event) {
        runSafely(
                "OPEN_CHAT_ROLE_CHANGED",
                event.roomId(),
                () -> creationService.createOpenChatRoleChanged(event)
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OpenChatRoomClosedApplicationEvent event) {
        runSafely(
                "OPEN_CHAT_ROOM_CLOSED",
                event.roomId(),
                () -> creationService.createOpenChatRoomClosed(event)
        );
    }

    private void runSafely(
            String notificationType,
            Long roomId,
            Runnable action
    ) {
        try {
            action.run();
        } catch (Exception exception) {
            log.error(
                    "Failed to create chat activity notification after source transaction commit. notificationType={}, roomId={}",
                    notificationType,
                    roomId,
                    exception
            );
        }
    }
}
