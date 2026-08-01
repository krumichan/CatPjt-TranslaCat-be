package jp.co.translacat.domain.chat.openchat.dto.response;

import java.util.List;

public record OpenChatBanListResponseDto(
        List<OpenChatBanListItemResponseDto> items,
        Long nextCursorId,
        boolean hasNext
) {

    public static OpenChatBanListResponseDto of(
            List<OpenChatBanListItemResponseDto> items,
            Long nextCursorId,
            boolean hasNext
    ) {
        return new OpenChatBanListResponseDto(
                List.copyOf(items),
                nextCursorId,
                hasNext
        );
    }
}
