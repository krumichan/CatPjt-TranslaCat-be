package jp.co.translacat.domain.support.dto;

import jp.co.translacat.domain.support.entity.SupportNotification;
import jp.co.translacat.domain.support.enums.SupportNotificationType;
import jp.co.translacat.domain.support.enums.SupportTargetType;

import java.time.LocalDateTime;

public record NotificationResponseDto(
        Long id,
        SupportNotificationType type,
        String title,
        SupportTargetType targetType,
        Long targetId,
        Long replyId,
        boolean isRead,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {

    public static NotificationResponseDto from(SupportNotification notification) {
        return new NotificationResponseDto(
                notification.getId(), notification.getType(), notification.getTitle(),
                notification.getTargetType(), notification.getTargetId(), notification.getReplyId(),
                notification.getReadAt() != null, notification.getReadAt(), notification.getCreatedAt()
        );
    }
}
