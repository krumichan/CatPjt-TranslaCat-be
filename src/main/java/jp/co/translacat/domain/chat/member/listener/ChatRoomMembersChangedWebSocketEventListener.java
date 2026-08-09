package jp.co.translacat.domain.chat.member.listener;

import jp.co.translacat.domain.chat.member.event.ChatRoomMembersChangedApplicationEvent;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatRoomMembersChangedWebSocketEventListener {

    private final ChatWebSocketEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatRoomMembersChangedApplicationEvent event) {
        eventPublisher.publishRoomMembersChanged(
                event.roomId(),
                event.occurredAt()
        );
    }
}
