package jp.co.translacat.domain.chat.notification.dto.response;

import java.util.List;

public record ChatNotificationActivityListResponseDto(
        List<ChatNotificationActivityItemResponseDto> items,
        Long nextCursorId,
        boolean hasNext
) {

    public static ChatNotificationActivityListResponseDto of(
            List<ChatNotificationActivityItemResponseDto> items,
            Long nextCursorId,
            boolean hasNext
    ) {
        return new ChatNotificationActivityListResponseDto(
                List.copyOf(items),
                nextCursorId,
                hasNext
        );
    }
}
