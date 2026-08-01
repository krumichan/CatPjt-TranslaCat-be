package jp.co.translacat.domain.chat.openchat.dto.response;

import java.time.LocalDateTime;

public record OpenChatBanListItemResponseDto(
        Long banId,
        Long targetOpenChatMemberId,
        String memberCode,
        String nickname,
        String profileImageUrl,
        LocalDateTime lastJoinedAt,
        LocalDateTime bannedAt,
        OpenChatBanActorResponseDto bannedBy,
        String reason,
        boolean releasable
) {
}
