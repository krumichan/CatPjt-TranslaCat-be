package jp.co.translacat.domain.chat.openchat.dto.response;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

public record OpenChatMembershipResponseDto(
        Long roomId,
        boolean active,
        ChatRoomMemberRole role,
        OpenChatMemberProfileResponseDto profile
) {
}
