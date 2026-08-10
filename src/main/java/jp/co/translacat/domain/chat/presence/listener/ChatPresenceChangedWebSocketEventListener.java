package jp.co.translacat.domain.chat.presence.listener;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "translacat.chat.presence",
        name = "enabled",
        havingValue = "true"
)
public class ChatPresenceChangedWebSocketEventListener {

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatWebSocketEventPublisher eventPublisher;

    @EventListener
    public void handle(ChatPresenceChangedApplicationEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }

        chatRoomMemberRepository
                .findByUserIdAndActiveTrueAndDeletedAtIsNull(event.userId())
                .stream()
                .map(ChatRoomMember::getChatRoom)
                .filter(chatRoom -> chatRoom != null && chatRoom.getId() != null)
                .map(chatRoom -> chatRoom.getId())
                .distinct()
                .forEach(roomId -> eventPublisher.publishPresenceChanged(
                        roomId,
                        event.userId(),
                        event.online(),
                        event.occurredAt()
                ));
    }
}
