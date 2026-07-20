package jp.co.translacat.domain.chat.member.dto.request;

import java.util.List;

public record ChatRoomMemberInvitationRequestDto(
        List<Long> targetUserIds,
        List<String> targetPublicIds
) {
}
