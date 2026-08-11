package jp.co.translacat.domain.chat.openchat.dto.websocket.event;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record OpenChatProfileUpdatedEventDto(
        String eventType,
        Long roomId,
        Long openChatMemberId,
        String memberCode,
        String nickname,
        String profileImageUrl,
        ChatRoomMemberRole role,
        @ChatUtcTimestamp LocalDateTime occurredAt
) {

    public static OpenChatProfileUpdatedEventDto of(
            Long roomId,
            Long openChatMemberId,
            String memberCode,
            String nickname,
            String profileImageUrl,
            ChatRoomMemberRole role,
            LocalDateTime occurredAt
    ) {
        return new OpenChatProfileUpdatedEventDto(
                ChatWebSocketEventType.OPEN_PROFILE_UPDATED.getEventName(),
                roomId,
                openChatMemberId,
                memberCode,
                nickname,
                profileImageUrl,
                role,
                occurredAt
        );
    }
}
