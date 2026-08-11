package jp.co.translacat.domain.chat.openchat.event;

import java.time.LocalDateTime;

public record OpenChatMemberBannedApplicationEvent(
        Long roomId,
        Long targetOpenChatMemberId,
        String targetUsername,
        String reason,
        LocalDateTime bannedAt,
        Long banId,
        Long actorUserId,
        LocalDateTime occurredAt
) {

    public OpenChatMemberBannedApplicationEvent(
            Long roomId,
            Long targetOpenChatMemberId,
            String targetUsername,
            String reason,
            LocalDateTime bannedAt,
            LocalDateTime occurredAt
    ) {
        this(
                roomId,
                targetOpenChatMemberId,
                targetUsername,
                reason,
                bannedAt,
                null,
                null,
                occurredAt
        );
    }

    public static OpenChatMemberBannedApplicationEvent of(
            Long roomId,
            Long targetOpenChatMemberId,
            String targetUsername,
            String reason,
            LocalDateTime bannedAt
    ) {
        return of(
                roomId,
                targetOpenChatMemberId,
                targetUsername,
                reason,
                bannedAt,
                null,
                null
        );
    }

    public static OpenChatMemberBannedApplicationEvent of(
            Long roomId,
            Long targetOpenChatMemberId,
            String targetUsername,
            String reason,
            LocalDateTime bannedAt,
            Long banId,
            Long actorUserId
    ) {
        return new OpenChatMemberBannedApplicationEvent(
                roomId,
                targetOpenChatMemberId,
                targetUsername,
                reason,
                bannedAt,
                banId,
                actorUserId,
                LocalDateTime.now()
        );
    }
}
