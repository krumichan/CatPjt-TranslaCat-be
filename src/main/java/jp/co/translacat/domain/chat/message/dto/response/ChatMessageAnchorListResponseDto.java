package jp.co.translacat.domain.chat.message.dto.response;

import java.util.List;

public record ChatMessageAnchorListResponseDto(
        List<ChatMessageResponseDto> messages,
        Long anchorMessageId,
        Long previousCursorId,
        boolean hasPrevious,
        Long nextCursorId,
        boolean hasNext
) {
    public static ChatMessageAnchorListResponseDto of(
            List<ChatMessageResponseDto> messages,
            Long anchorMessageId,
            Long previousCursorId,
            boolean hasPrevious,
            Long nextCursorId,
            boolean hasNext
    ) {
        return new ChatMessageAnchorListResponseDto(
                messages,
                anchorMessageId,
                previousCursorId,
                hasPrevious,
                nextCursorId,
                hasNext
        );
    }
}
