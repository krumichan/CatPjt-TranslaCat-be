package jp.co.translacat.domain.chat.openchat.dto.response;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

public record OpenChatBanActorResponseDto(
        Long openChatMemberId,
        String nickname,
        ChatRoomMemberRole role
) {
}
