package jp.co.translacat.domain.chat.openchat.event;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

import java.time.LocalDateTime;

public record OpenChatProfileUpdatedApplicationEvent(
        Long roomId,
        Long openChatMemberId,
        String memberCode,
        String nickname,
        String profileImageObjectKey,
        ChatRoomMemberRole role,
        LocalDateTime occurredAt
) {

    public static OpenChatProfileUpdatedApplicationEvent of(
            Long roomId,
            Long openChatMemberId,
            String memberCode,
            String nickname,
            String profileImageObjectKey,
            ChatRoomMemberRole role
    ) {
        return new OpenChatProfileUpdatedApplicationEvent(
                roomId,
                openChatMemberId,
                memberCode,
                nickname,
                profileImageObjectKey,
                role,
                LocalDateTime.now()
        );
    }
}
