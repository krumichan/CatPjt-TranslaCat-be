package jp.co.translacat.domain.chat.openchat.repository;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

import java.time.LocalDateTime;

public record OpenChatMemberProfileQueryRow(
        Long chatRoomId,
        Long openChatMemberId,
        String memberCode,
        String nickname,
        String profileImageObjectKey,
        ChatRoomMemberRole role,
        LocalDateTime joinedAt
) {
}
