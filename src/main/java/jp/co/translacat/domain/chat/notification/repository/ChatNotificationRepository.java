package jp.co.translacat.domain.chat.notification.repository;

import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatNotificationRepository
        extends JpaRepository<ChatNotification, Long>,
        ChatNotificationRepositoryCustom {

    Optional<ChatNotification>
    findByIdAndRecipientUser_IdAndDeletedAtIsNull(
            Long notificationId,
            Long recipientUserId
    );
}
