package jp.co.translacat.domain.chat.openchat.ban.repository;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

import java.time.LocalDateTime;

public record OpenChatBanQueryRow(
        Long banId,
        Long roomId,
        Long targetOpenChatMemberId,
        String memberCode,
        String nickname,
        String profileImageObjectKey,
        LocalDateTime lastJoinedAt,
        Long bannedByOpenChatMemberId,
        String bannedByNickname,
        ChatRoomMemberRole bannedByRole,
        LocalDateTime bannedAt,
        String reason,
        ChatRoomMemberRole targetRoleSnapshot
) {
}
