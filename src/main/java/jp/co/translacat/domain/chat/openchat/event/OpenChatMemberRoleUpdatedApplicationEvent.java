package jp.co.translacat.domain.chat.openchat.event;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

import java.time.LocalDateTime;

public record OpenChatMemberRoleUpdatedApplicationEvent(
        Long roomId,
        Long targetOpenChatMemberId,
        ChatRoomMemberRole role,
        Long actorUserId,
        LocalDateTime occurredAt
) {

    public OpenChatMemberRoleUpdatedApplicationEvent(
            Long roomId,
            Long targetOpenChatMemberId,
            ChatRoomMemberRole role,
            LocalDateTime occurredAt
    ) {
        this(
                roomId,
                targetOpenChatMemberId,
                role,
                null,
                occurredAt
        );
    }

    public static OpenChatMemberRoleUpdatedApplicationEvent of(
            Long roomId,
            Long targetOpenChatMemberId,
            ChatRoomMemberRole role
    ) {
        return of(
                roomId,
                targetOpenChatMemberId,
                role,
                null
        );
    }

    public static OpenChatMemberRoleUpdatedApplicationEvent of(
            Long roomId,
            Long targetOpenChatMemberId,
            ChatRoomMemberRole role,
            Long actorUserId
    ) {
        return new OpenChatMemberRoleUpdatedApplicationEvent(
                roomId,
                targetOpenChatMemberId,
                role,
                actorUserId,
                LocalDateTime.now()
        );
    }
}
