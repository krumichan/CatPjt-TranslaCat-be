package jp.co.translacat.domain.chat.notification.repository;

import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import jp.co.translacat.domain.chat.notification.enums.ChatNotificationType;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:translacat-activity-notification-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class ChatNotificationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatNotificationRepository repository;

    @Test
    void findsOnlyRecipientActivitiesWithUnreadFilterAndCursor() {
        User recipient = persistUser(
                "activity-recipient@translacat.test",
                "activity-recipient",
                "ACTRECIP01"
        );
        User other = persistUser(
                "activity-other@translacat.test",
                "activity-other",
                "ACTOTHER01"
        );
        ChatRoom room = persistRoom("activity-room", recipient);

        ChatNotification oldest = persistNotification(
                recipient,
                ChatNotificationType.CHAT_INVITATION,
                room,
                other,
                "{\"roomName\":\"activity-room\"}",
                "invite:1"
        );
        ChatNotification middle = persistNotification(
                recipient,
                ChatNotificationType.OPEN_CHAT_ROLE_CHANGED,
                room,
                other,
                "{\"newRole\":\"ADMIN\"}",
                "role:1"
        );
        middle.markRead(LocalDateTime.now().minusMinutes(1));
        ChatNotification newest = persistNotification(
                recipient,
                ChatNotificationType.OPEN_CHAT_ROOM_CLOSED,
                room,
                null,
                "{\"roomName\":\"activity-room\"}",
                "closed:1"
        );
        persistNotification(
                other,
                ChatNotificationType.OPEN_CHAT_KICKED,
                room,
                recipient,
                "{}",
                "kick:other"
        );
        entityManager.flush();

        List<ChatNotification> firstPage = repository.findActivityPage(
                recipient.getId(),
                false,
                null,
                2
        );

        assertThat(firstPage)
                .extracting(ChatNotification::getId)
                .containsExactly(newest.getId(), middle.getId());

        List<ChatNotification> secondPage = repository.findActivityPage(
                recipient.getId(),
                false,
                middle.getId(),
                2
        );
        assertThat(secondPage)
                .extracting(ChatNotification::getId)
                .containsExactly(oldest.getId());

        List<ChatNotification> unreadOnly = repository.findActivityPage(
                recipient.getId(),
                true,
                null,
                10
        );
        assertThat(unreadOnly)
                .extracting(ChatNotification::getId)
                .containsExactly(newest.getId(), oldest.getId());
        assertThat(repository.countUnreadActivities(recipient.getId()))
                .isEqualTo(2L);
    }

    @Test
    void marksOnlyRecipientsUnreadActivitiesAsRead() {
        User recipient = persistUser(
                "readall-recipient@translacat.test",
                "readall-recipient",
                "READALL001"
        );
        User other = persistUser(
                "readall-other@translacat.test",
                "readall-other",
                "READALLO02"
        );
        ChatRoom room = persistRoom("readall-room", recipient);

        ChatNotification one = persistNotification(
                recipient,
                ChatNotificationType.CHAT_INVITATION,
                room,
                other,
                "{}",
                "readall:1"
        );
        ChatNotification two = persistNotification(
                recipient,
                ChatNotificationType.OPEN_CHAT_ROLE_CHANGED,
                room,
                other,
                "{}",
                "readall:2"
        );
        ChatNotification others = persistNotification(
                other,
                ChatNotificationType.OPEN_CHAT_KICKED,
                room,
                recipient,
                "{}",
                "readall:other"
        );
        entityManager.flush();
        entityManager.clear();

        LocalDateTime readAt = LocalDateTime.now();
        long updated = repository.markAllReadByRecipientUserId(
                recipient.getId(),
                readAt
        );
        entityManager.clear();

        assertThat(updated).isEqualTo(2L);
        assertThat(repository.countUnreadActivities(recipient.getId()))
                .isZero();
        assertThat(repository.countUnreadActivities(other.getId()))
                .isEqualTo(1L);

        ChatNotification reloadedOne = repository.findById(one.getId())
                .orElseThrow();
        ChatNotification reloadedTwo = repository.findById(two.getId())
                .orElseThrow();
        ChatNotification reloadedOther = repository.findById(others.getId())
                .orElseThrow();
        assertThat(reloadedOne.isRead()).isTrue();
        assertThat(reloadedOne.getReadAt()).isNotNull();
        assertThat(reloadedTwo.isRead()).isTrue();
        assertThat(reloadedOther.isRead()).isFalse();
    }

    @Test
    void rejectsDuplicateSourceEventForSameRecipientAndType() {
        User recipient = persistUser(
                "duplicate-recipient@translacat.test",
                "duplicate-recipient",
                "DUPRECIP01"
        );
        User actor = persistUser(
                "duplicate-actor@translacat.test",
                "duplicate-actor",
                "DUPACTOR01"
        );
        ChatRoom room = persistRoom("duplicate-room", recipient);

        persistNotification(
                recipient,
                ChatNotificationType.OPEN_CHAT_ROLE_CHANGED,
                room,
                actor,
                "{}",
                "role-event:100"
        );
        entityManager.flush();

        ChatNotification duplicate = ChatNotification.create(
                recipient,
                ChatNotificationType.OPEN_CHAT_ROLE_CHANGED,
                room,
                actor,
                "{}",
                "role-event:100"
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> repository.saveAndFlush(duplicate)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repositoryLookupDoesNotExposeOtherRecipientsNotification() {
        User recipient = persistUser(
                "owner-recipient@translacat.test",
                "owner-recipient",
                "OWNRECIP01"
        );
        User other = persistUser(
                "owner-other@translacat.test",
                "owner-other",
                "OWNOTHER01"
        );
        ChatRoom room = persistRoom("owner-room", recipient);
        ChatNotification notification = persistNotification(
                recipient,
                ChatNotificationType.CHAT_INVITATION,
                room,
                other,
                "{}",
                "owner:1"
        );
        entityManager.flush();

        assertThat(repository
                .findByIdAndRecipientUser_IdAndDeletedAtIsNull(
                        notification.getId(),
                        recipient.getId()
                ))
                .isPresent();
        assertThat(repository
                .findByIdAndRecipientUser_IdAndDeletedAtIsNull(
                        notification.getId(),
                        other.getId()
                ))
                .isEmpty();
    }

    private User persistUser(
            String email,
            String username,
            String publicId
    ) {
        return entityManager.persist(User.createLocalUser(
                email,
                "password",
                username,
                Role.USER,
                publicId
        ));
    }

    private ChatRoom persistRoom(String name, User owner) {
        return entityManager.persist(ChatRoom.createGroupRoom(
                name,
                null,
                owner,
                ChatRoomSourceType.MANUAL
        ));
    }

    private ChatNotification persistNotification(
            User recipient,
            ChatNotificationType type,
            ChatRoom room,
            User actor,
            String payloadJson,
            String sourceEventKey
    ) {
        return entityManager.persist(ChatNotification.create(
                recipient,
                type,
                room,
                actor,
                payloadJson,
                sourceEventKey
        ));
    }
}
