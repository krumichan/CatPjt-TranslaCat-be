package jp.co.translacat.domain.voice.dto.response;

import java.util.List;

public record VoiceSegmentListResponseDto(
        List<VoiceSegmentResponseDto> items,
        Long nextCursor
) {
}
