package jp.co.translacat.domain.chat.notification.repository;

import jp.co.translacat.domain.chat.notification.entity.ChatNotification;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatNotificationRepositoryCustom {

    List<ChatNotification> findActivityPage(
            Long recipientUserId,
            boolean onlyUnread,
            Long cursorId,
            int limit
    );

    long countUnreadActivities(Long recipientUserId);

    long markAllReadByRecipientUserId(
            Long recipientUserId,
            LocalDateTime readAt
    );
}
