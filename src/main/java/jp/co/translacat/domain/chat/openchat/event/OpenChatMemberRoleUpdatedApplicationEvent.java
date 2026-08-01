package jp.co.translacat.domain.chat.openchat.event;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

import java.time.LocalDateTime;

public record OpenChatMemberRoleUpdatedApplicationEvent(
        Long roomId,
        Long targetOpenChatMemberId,
        ChatRoomMemberRole role,
        LocalDateTime occurredAt
) {

    public static OpenChatMemberRoleUpdatedApplicationEvent of(
            Long roomId,
            Long targetOpenChatMemberId,
            ChatRoomMemberRole role
    ) {
        return new OpenChatMemberRoleUpdatedApplicationEvent(
                roomId,
                targetOpenChatMemberId,
                role,
                LocalDateTime.now()
        );
    }
}
