package jp.co.translacat.domain.chat.openchat.dto.response;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

public record OpenChatMessageSenderResponseDto(
        Long openChatMemberId,
        String memberCode,
        String nickname,
        String profileImageUrl,
        ChatRoomMemberRole role
) {
}
