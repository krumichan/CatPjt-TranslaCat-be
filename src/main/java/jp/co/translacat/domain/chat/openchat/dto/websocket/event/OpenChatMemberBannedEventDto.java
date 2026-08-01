package jp.co.translacat.domain.chat.openchat.dto.websocket.event;

import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record OpenChatMemberBannedEventDto(
        String eventType,
        Long roomId,
        Long targetOpenChatMemberId,
        String reason,
        LocalDateTime bannedAt,
        LocalDateTime occurredAt
) {

    public static OpenChatMemberBannedEventDto of(
            Long roomId,
            Long targetOpenChatMemberId,
            String reason,
            LocalDateTime bannedAt,
            LocalDateTime occurredAt
    ) {
        return new OpenChatMemberBannedEventDto(
                ChatWebSocketEventType.MEMBER_BANNED.getEventName(),
                roomId,
                targetOpenChatMemberId,
                reason,
                bannedAt,
                occurredAt
        );
    }
}
