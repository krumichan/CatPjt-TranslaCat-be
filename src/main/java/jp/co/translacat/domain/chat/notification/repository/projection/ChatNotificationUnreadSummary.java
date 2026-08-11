package jp.co.translacat.domain.chat.notification.repository.projection;

public record ChatNotificationUnreadSummary(
        long unreadMessageCount,
        long unreadRoomCount
) {
    public static ChatNotificationUnreadSummary empty() {
        return new ChatNotificationUnreadSummary(0L, 0L);
    }
}
