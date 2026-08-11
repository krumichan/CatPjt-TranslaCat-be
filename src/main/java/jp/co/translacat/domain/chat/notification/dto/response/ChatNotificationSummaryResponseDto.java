package jp.co.translacat.domain.chat.notification.dto.response;

public record ChatNotificationSummaryResponseDto(
        long unreadChatMessageCount,
        long unreadChatRoomCount,
        long unreadActivityCount,
        long totalAttentionCount
) {

    public static ChatNotificationSummaryResponseDto of(
            long unreadChatMessageCount,
            long unreadChatRoomCount,
            long unreadActivityCount
    ) {
        return new ChatNotificationSummaryResponseDto(
                unreadChatMessageCount,
                unreadChatRoomCount,
                unreadActivityCount,
                unreadChatMessageCount + unreadActivityCount
        );
    }
}
