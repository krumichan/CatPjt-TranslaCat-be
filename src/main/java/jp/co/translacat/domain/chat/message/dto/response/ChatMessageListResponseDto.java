package jp.co.translacat.domain.chat.message.dto.response;

import java.util.List;

public record ChatMessageListResponseDto(
        List<ChatMessageResponseDto> messages,
        Long nextCursorId,
        boolean hasNext
) {

    public static ChatMessageListResponseDto of(
            List<ChatMessageResponseDto> messages,
            Long nextCursorId,
            boolean hasNext
    ) {
        return new ChatMessageListResponseDto(
                messages,
                nextCursorId,
                hasNext
        );
    }
}