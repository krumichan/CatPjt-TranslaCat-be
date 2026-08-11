package jp.co.translacat.domain.chat.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityListResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationSummaryResponseDto;
import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import jp.co.translacat.domain.chat.notification.enums.ChatNotificationType;
import jp.co.translacat.domain.chat.notification.repository.ChatNotificationChatQueryRepository;
import jp.co.translacat.domain.chat.notification.repository.ChatNotificationRepository;
import jp.co.translacat.domain.chat.notification.repository.projection.ChatNotificationUnreadSummary;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.profile.storage.service.UserProfileImageUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatNotificationQueryServiceActivityTest {

    @Mock
    private ChatNotificationChatQueryRepository chatQueryRepository;
    @Mock
    private ChatNotificationRepository notificationRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserProfileImageUrlResolver userProfileImageUrlResolver;
    @Mock
    private OpenChatMemberProfileRepository openChatMemberProfileRepository;

    private ChatNotificationQueryService service;

    @BeforeEach
    void setUp() {
        service = new ChatNotificationQueryService(
                chatQueryRepository,
                notificationRepository,
                new ChatNotificationActivityResponseMapper(
                        new ObjectMapper()
                ),
                chatMessageRepository,
                chatRoomMemberRepository,
                userProfileRepository,
                userProfileImageUrlResolver,
                openChatMemberProfileRepository
        );
    }

    @Test
    void summaryIncludesUnreadActivityCount() {
        when(chatQueryRepository.summarizeUnreadChats(1L))
                .thenReturn(new ChatNotificationUnreadSummary(7L, 2L));
        when(notificationRepository.countUnreadActivities(1L))
                .thenReturn(3L);

        ChatNotificationSummaryResponseDto response =
                service.getSummary(1L);

        assertThat(response.unreadChatMessageCount()).isEqualTo(7L);
        assertThat(response.unreadChatRoomCount()).isEqualTo(2L);
        assertThat(response.unreadActivityCount()).isEqualTo(3L);
        assertThat(response.totalAttentionCount()).isEqualTo(10L);
    }

    @Test
    void activityListUsesCursorPaginationAndPayloadMapping() {
        User recipient = User.createLocalUser(
                "query-activity@translacat.test",
                "password",
                "query-activity",
                Role.USER,
                "QUERYACT01"
        );
        recipient.setId(1L);
        ChatNotification newest = createNotification(
                30L,
                recipient,
                "{\"roomName\":\"newest\"}",
                "activity:30"
        );
        ChatNotification second = createNotification(
                20L,
                recipient,
                "{\"roomName\":\"second\"}",
                "activity:20"
        );
        ChatNotification extra = createNotification(
                10L,
                recipient,
                "{\"roomName\":\"extra\"}",
                "activity:10"
        );
        when(notificationRepository.findActivityPage(
                1L,
                true,
                null,
                3
        )).thenReturn(List.of(newest, second, extra));

        ChatNotificationActivityListResponseDto response =
                service.getActivities(1L, true, null, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().id()).isEqualTo(30L);
        assertThat(response.items().getFirst().payload())
                .containsEntry("roomName", "newest");
        assertThat(response.nextCursorId()).isEqualTo(20L);
        assertThat(response.hasNext()).isTrue();
    }

    private ChatNotification createNotification(
            Long id,
            User recipient,
            String payload,
            String sourceEventKey
    ) {
        ChatNotification notification = ChatNotification.create(
                recipient,
                ChatNotificationType.OPEN_CHAT_ROOM_CLOSED,
                null,
                null,
                payload,
                sourceEventKey
        );
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(
                notification,
                "createdAt",
                LocalDateTime.now()
        );
        return notification;
    }
}
