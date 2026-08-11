package jp.co.translacat.domain.chat.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityItemResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityReadAllResponseDto;
import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import jp.co.translacat.domain.chat.notification.enums.ChatNotificationType;
import jp.co.translacat.domain.chat.notification.repository.ChatNotificationRepository;
import jp.co.translacat.domain.chat.notification.support.ChatNotificationErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatNotificationActivityCommandServiceTest {

    @Mock
    private ChatNotificationRepository notificationRepository;

    private ChatNotificationActivityCommandService service;

    @BeforeEach
    void setUp() {
        ChatNotificationActivityResponseMapper mapper =
                new ChatNotificationActivityResponseMapper(
                        new ObjectMapper()
                );
        service = new ChatNotificationActivityCommandService(
                notificationRepository,
                mapper
        );
    }

    @Test
    void marksOwnNotificationAsReadIdempotently() {
        User recipient = createUser(1L, "recipient", "RECIPIENT1");
        ChatNotification notification = ChatNotification.create(
                recipient,
                ChatNotificationType.CHAT_INVITATION,
                null,
                null,
                "{\"roomName\":\"group\"}",
                "invite:100"
        );
        ReflectionTestUtils.setField(notification, "id", 100L);
        ReflectionTestUtils.setField(
                notification,
                "createdAt",
                LocalDateTime.now().minusMinutes(5)
        );
        when(notificationRepository
                .findByIdAndRecipientUser_IdAndDeletedAtIsNull(100L, 1L))
                .thenReturn(Optional.of(notification));

        ChatNotificationActivityItemResponseDto first =
                service.markAsRead(1L, 100L);
        LocalDateTime firstReadAt = first.readAt();
        ChatNotificationActivityItemResponseDto second =
                service.markAsRead(1L, 100L);

        assertThat(first.isRead()).isTrue();
        assertThat(first.payload()).containsEntry("roomName", "group");
        assertThat(second.isRead()).isTrue();
        assertThat(second.readAt()).isEqualTo(firstReadAt);
    }

    @Test
    void doesNotExposeOtherRecipientsNotification() {
        when(notificationRepository
                .findByIdAndRecipientUser_IdAndDeletedAtIsNull(100L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(
                        ((BusinessException) error).getErrorCode()
                ).isEqualTo(ChatNotificationErrorCode.NOT_FOUND));
    }

    @Test
    void marksAllActivitiesAsRead() {
        when(notificationRepository.markAllReadByRecipientUserId(
                eq(1L),
                any(LocalDateTime.class)
        )).thenReturn(3L);

        ChatNotificationActivityReadAllResponseDto response =
                service.markAllAsRead(1L);

        assertThat(response.updatedCount()).isEqualTo(3L);
        verify(notificationRepository).markAllReadByRecipientUserId(
                eq(1L),
                any(LocalDateTime.class)
        );
    }

    private User createUser(
            Long id,
            String username,
            String publicId
    ) {
        User user = User.createLocalUser(
                username + "@translacat.test",
                "password",
                username,
                Role.USER,
                publicId
        );
        user.setId(id);
        return user;
    }
}
