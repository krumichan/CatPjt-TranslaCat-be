package jp.co.translacat.domain.chat.openchat.event;

import java.time.LocalDateTime;

public record OpenChatMemberBannedApplicationEvent(
        Long roomId,
        Long targetOpenChatMemberId,
        String targetUsername,
        String reason,
        LocalDateTime bannedAt,
        LocalDateTime occurredAt
) {

    public static OpenChatMemberBannedApplicationEvent of(
            Long roomId,
            Long targetOpenChatMemberId,
            String targetUsername,
            String reason,
            LocalDateTime bannedAt
    ) {
        return new OpenChatMemberBannedApplicationEvent(
                roomId,
                targetOpenChatMemberId,
                targetUsername,
                reason,
                bannedAt,
                LocalDateTime.now()
        );
    }
}
