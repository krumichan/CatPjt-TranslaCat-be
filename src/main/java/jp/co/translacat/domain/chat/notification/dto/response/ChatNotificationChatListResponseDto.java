package jp.co.translacat.domain.chat.notification.dto.response;

import java.util.List;

public record ChatNotificationChatListResponseDto(
        List<ChatNotificationChatItemResponseDto> items,
        Long nextCursorMessageId,
        boolean hasNext
) {

    public static ChatNotificationChatListResponseDto of(
            List<ChatNotificationChatItemResponseDto> items,
            Long nextCursorMessageId,
            boolean hasNext
    ) {
        return new ChatNotificationChatListResponseDto(
                List.copyOf(items),
                nextCursorMessageId,
                hasNext
        );
    }
}
