package jp.co.translacat.domain.chat.openchat.dto.response;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;

import java.time.LocalDateTime;

public record OpenChatBanListItemResponseDto(
        Long banId,
        Long targetOpenChatMemberId,
        String memberCode,
        String nickname,
        String profileImageUrl,
        @ChatUtcTimestamp LocalDateTime lastJoinedAt,
        @ChatUtcTimestamp LocalDateTime bannedAt,
        OpenChatBanActorResponseDto bannedBy,
        String reason,
        boolean releasable
) {
}
