package jp.co.translacat.domain.languagelearning.speaking.turn.dto.response;

import java.time.LocalDateTime;

public record SpeakingTurnUploadGrantResponseDto(
        Long turnId,
        int turnIndex,
        String uploadToken,
        String uploadUrl,
        LocalDateTime expiresAt
) {
}
