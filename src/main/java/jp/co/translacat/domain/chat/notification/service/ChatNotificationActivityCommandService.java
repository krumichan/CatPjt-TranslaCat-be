package jp.co.translacat.domain.chat.notification.service;

import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityItemResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityReadAllResponseDto;
import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import jp.co.translacat.domain.chat.notification.repository.ChatNotificationRepository;
import jp.co.translacat.domain.chat.notification.support.ChatNotificationErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatNotificationActivityCommandService {

    private final ChatNotificationRepository notificationRepository;
    private final ChatNotificationActivityResponseMapper responseMapper;

    public ChatNotificationActivityItemResponseDto markAsRead(
            Long loginUserId,
            Long notificationId
    ) {
        validateNotificationId(notificationId);

        ChatNotification notification = notificationRepository
                .findByIdAndRecipientUser_IdAndDeletedAtIsNull(
                        notificationId,
                        loginUserId
                )
                .orElseThrow(() -> new BusinessException(
                        "활동 알림을 찾을 수 없습니다.",
                        ChatNotificationErrorCode.NOT_FOUND
                ));

        notification.markRead(LocalDateTime.now());
        return responseMapper.toResponse(notification);
    }

    public ChatNotificationActivityReadAllResponseDto markAllAsRead(
            Long loginUserId
    ) {
        long updatedCount = notificationRepository
                .markAllReadByRecipientUserId(
                        loginUserId,
                        LocalDateTime.now()
                );
        return new ChatNotificationActivityReadAllResponseDto(
                updatedCount
        );
    }

    private void validateNotificationId(Long notificationId) {
        if (notificationId == null || notificationId <= 0L) {
            throw new BusinessException(
                    "notificationId는 1 이상이어야 합니다.",
                    ChatNotificationErrorCode.NOT_FOUND
            );
        }
    }
}
