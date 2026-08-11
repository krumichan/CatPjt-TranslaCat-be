package jp.co.translacat.domain.chat.notification.dto.response;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.message.enums.ChatMessageType;

import java.time.LocalDateTime;

public record ChatNotificationLatestMessageResponseDto(
        Long id,
        String senderDisplayName,
        ChatMessageType messageType,
        String contentPreview,
        @ChatUtcTimestamp LocalDateTime createdAt
) {
}
