package jp.co.translacat.domain.chat.notification.dto.response;

import jp.co.translacat.domain.chat.notification.enums.ChatNotificationType;

import java.time.LocalDateTime;
import java.util.Map;

public record ChatNotificationActivityItemResponseDto(
        Long id,
        ChatNotificationType notificationType,
        Long roomId,
        Map<String, Object> payload,
        boolean isRead,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
