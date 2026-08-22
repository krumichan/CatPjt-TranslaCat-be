package jp.co.translacat.domain.voice.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record VoiceSessionListResponseDto(
        List<VoiceSessionResponseDto> items,
        LocalDateTime nextCursor
) {
}
