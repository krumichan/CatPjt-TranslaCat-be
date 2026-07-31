package jp.co.translacat.domain.chat.openchat.dto.response;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

import java.time.LocalDateTime;

public record OpenChatMemberProfileResponseDto(
        Long openChatMemberId,
        String memberCode,
        String nickname,
        String profileImageUrl,
        ChatRoomMemberRole role,
        LocalDateTime joinedAt
) {
}
