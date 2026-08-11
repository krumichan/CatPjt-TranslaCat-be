package jp.co.translacat.domain.chat.notification.repository;

import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import jp.co.translacat.domain.chat.notification.enums.ChatNotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatNotificationRepository
        extends JpaRepository<ChatNotification, Long>,
        ChatNotificationRepositoryCustom {

    boolean existsByRecipientUser_IdAndNotificationTypeAndSourceEventKey(
            Long recipientUserId,
            ChatNotificationType notificationType,
            String sourceEventKey
    );

    Optional<ChatNotification>
    findByIdAndRecipientUser_IdAndDeletedAtIsNull(
            Long notificationId,
            Long recipientUserId
    );
}
