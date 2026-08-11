package jp.co.translacat.domain.chat.notification.dto.response;

import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

public record ChatNotificationChatItemResponseDto(
        Long roomId,
        ChatRoomType roomType,
        ChatRoomSourceType sourceType,
        String roomDisplayName,
        String roomAvatarUrl,
        ChatNotificationLatestMessageResponseDto latestMessage,
        long unreadCount,
        Long firstUnreadMessageId
) {
}
