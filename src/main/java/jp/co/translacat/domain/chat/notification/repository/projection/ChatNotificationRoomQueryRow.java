package jp.co.translacat.domain.chat.notification.repository.projection;

import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

public record ChatNotificationRoomQueryRow(
        Long roomId,
        ChatRoomType roomType,
        ChatRoomSourceType sourceType,
        String roomName,
        Long latestMessageId,
        long unreadCount,
        Long firstUnreadMessageId
) {
}
