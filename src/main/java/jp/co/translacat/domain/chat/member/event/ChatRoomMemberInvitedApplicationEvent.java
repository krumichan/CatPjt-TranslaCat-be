package jp.co.translacat.domain.chat.member.event;

import java.time.LocalDateTime;

public record ChatRoomMemberInvitedApplicationEvent(
        Long roomId,
        Long recipientUserId,
        Long actorUserId,
        Long chatRoomMemberId,
        LocalDateTime joinedAt,
        LocalDateTime occurredAt
) {

    private static final String SOURCE_PREFIX = "chat-invitation:";

    public static ChatRoomMemberInvitedApplicationEvent of(
            Long roomId,
            Long recipientUserId,
            Long actorUserId,
            Long chatRoomMemberId,
            LocalDateTime joinedAt
    ) {
        return new ChatRoomMemberInvitedApplicationEvent(
                roomId,
                recipientUserId,
                actorUserId,
                chatRoomMemberId,
                joinedAt,
                LocalDateTime.now()
        );
    }

    public String sourceEventKey() {
        return SOURCE_PREFIX
                + roomId
                + ":"
                + chatRoomMemberId
                + ":"
                + joinedAt;
    }
}
