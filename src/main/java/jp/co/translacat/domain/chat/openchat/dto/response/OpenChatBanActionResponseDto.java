package jp.co.translacat.domain.chat.openchat.dto.response;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;

import java.time.LocalDateTime;

public record OpenChatBanActionResponseDto(
        Long roomId,
        Long banId,
        Long targetOpenChatMemberId,
        boolean active,
        @ChatUtcTimestamp LocalDateTime bannedAt,
        @ChatUtcTimestamp LocalDateTime releasedAt
) {
}
