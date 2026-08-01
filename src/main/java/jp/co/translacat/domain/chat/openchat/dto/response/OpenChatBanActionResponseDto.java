package jp.co.translacat.domain.chat.openchat.dto.response;

import java.time.LocalDateTime;

public record OpenChatBanActionResponseDto(
        Long roomId,
        Long banId,
        Long targetOpenChatMemberId,
        boolean active,
        LocalDateTime bannedAt,
        LocalDateTime releasedAt
) {
}
