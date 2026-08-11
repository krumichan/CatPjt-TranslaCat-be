package jp.co.translacat.domain.chat.openchat.dto.websocket.event;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record OpenChatMemberRoleUpdatedEventDto(
        String eventType,
        Long roomId,
        Long targetOpenChatMemberId,
        ChatRoomMemberRole role,
        @ChatUtcTimestamp LocalDateTime occurredAt
) {

    public static OpenChatMemberRoleUpdatedEventDto of(
            Long roomId,
            Long targetOpenChatMemberId,
            ChatRoomMemberRole role,
            LocalDateTime occurredAt
    ) {
        return new OpenChatMemberRoleUpdatedEventDto(
                ChatWebSocketEventType.MEMBER_ROLE_UPDATED.getEventName(),
                roomId,
                targetOpenChatMemberId,
                role,
                occurredAt
        );
    }
}
