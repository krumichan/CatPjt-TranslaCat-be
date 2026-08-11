package jp.co.translacat.domain.chat.notification.event;

import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityItemResponseDto;

import java.time.LocalDateTime;

public record ChatNotificationCreatedApplicationEvent(
        String destinationUsername,
        ChatNotificationActivityItemResponseDto notification,
        LocalDateTime occurredAt
) {

    public static ChatNotificationCreatedApplicationEvent of(
            String destinationUsername,
            ChatNotificationActivityItemResponseDto notification
    ) {
        return new ChatNotificationCreatedApplicationEvent(
                destinationUsername,
                notification,
                LocalDateTime.now()
        );
    }
}
