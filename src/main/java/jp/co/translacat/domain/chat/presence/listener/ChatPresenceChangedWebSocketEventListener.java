package jp.co.translacat.domain.chat.presence.listener;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceVisibilityPolicy;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import jp.co.translacat.domain.user.entity.User;
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
    private final ChatPresenceVisibilityPolicy visibilityPolicy;

    @EventListener
    public void handle(ChatPresenceChangedApplicationEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }

        chatRoomMemberRepository
                .findByUserIdAndActiveTrueAndDeletedAtIsNull(event.userId())
                .stream()
                .filter(this::isPublishableMember)
                .forEach(member -> {
                    ChatRoom room = member.getChatRoom();
                    eventPublisher.publishPresenceChanged(
                            room.getId(),
                            room.getRoomType(),
                            resolveMemberRef(member),
                            event.online(),
                            event.occurredAt()
                    );
                });
    }

    private boolean isPublishableMember(ChatRoomMember member) {
        if (member == null || member.getChatRoom() == null) {
            return false;
        }
        ChatRoom room = member.getChatRoom();
        if (room.getId() == null || room.getRoomType() == null) {
            return false;
        }
        return visibilityPolicy.isVisible(room.getId())
                && resolveMemberRef(member) != null;
    }

    private String resolveMemberRef(ChatRoomMember member) {
        ChatRoom room = member.getChatRoom();
        if (room.getRoomType() == ChatRoomType.DIRECT) {
            User user = member.getUser();
            return user != null && hasText(user.getPublicId())
                    ? user.getPublicId()
                    : null;
        }

        return member.getId() != null
                ? member.getId().toString()
                : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
