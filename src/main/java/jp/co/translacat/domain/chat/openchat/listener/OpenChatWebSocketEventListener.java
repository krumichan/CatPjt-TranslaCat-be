package jp.co.translacat.domain.chat.openchat.listener;

import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberBannedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberRoleUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatProfileUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatRoomClosedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileImageUrlResolver;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OpenChatWebSocketEventListener {

    private final ChatWebSocketEventPublisher eventPublisher;
    private final OpenChatProfileImageUrlResolver imageUrlResolver;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OpenChatProfileUpdatedApplicationEvent event) {
        eventPublisher.publishOpenChatProfileUpdated(
                event.roomId(),
                event.openChatMemberId(),
                event.memberCode(),
                event.nickname(),
                imageUrlResolver.resolve(event.profileImageObjectKey()),
                event.role(),
                event.occurredAt()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OpenChatMemberRoleUpdatedApplicationEvent event) {
        eventPublisher.publishOpenChatMemberRoleUpdated(
                event.roomId(),
                event.targetOpenChatMemberId(),
                event.role(),
                event.occurredAt()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OpenChatMemberBannedApplicationEvent event) {
        eventPublisher.publishOpenChatMemberBanned(
                event.roomId(),
                event.targetOpenChatMemberId(),
                event.targetUsername(),
                event.reason(),
                event.bannedAt(),
                event.occurredAt()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OpenChatRoomClosedApplicationEvent event) {
        eventPublisher.publishOpenChatRoomClosed(
                event.roomId(),
                event.closedAt(),
                event.occurredAt()
        );
    }
}
