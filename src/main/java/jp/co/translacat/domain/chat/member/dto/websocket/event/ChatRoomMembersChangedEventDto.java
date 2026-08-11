package jp.co.translacat.domain.chat.member.dto.websocket.event;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatRoomMembersChangedEventDto(
        String eventType,
        Long roomId,
        @ChatUtcTimestamp LocalDateTime occurredAt
) {
    public static ChatRoomMembersChangedEventDto of(
            Long roomId,
            LocalDateTime occurredAt
    ) {
        return new ChatRoomMembersChangedEventDto(
                ChatWebSocketEventType.MEMBERS_CHANGED.getEventName(),
                roomId,
                occurredAt
        );
    }
}
