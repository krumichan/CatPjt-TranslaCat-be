package jp.co.translacat.domain.chat.notification.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.notification.repository.projection.ChatNotificationRoomQueryRow;
import jp.co.translacat.domain.chat.notification.repository.projection.ChatNotificationUnreadSummary;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:translacat-notification-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import({
        QueryDslConfig.class,
        ChatNotificationChatQueryRepositoryImpl.class
})
class ChatNotificationChatQueryRepositoryImplTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatNotificationChatQueryRepository repository;

    @Test
    void findsUnreadRoomsWithFirstUnreadAndCursorPagination() {
        User reader = persistUser(
                "noti-reader@translacat.test",
                "noti-reader",
                "NOTIREADER1"
        );
        User sender = persistUser(
                "noti-sender@translacat.test",
                "noti-sender",
                "NOTISENDER1"
        );

        ChatRoom olderRoom = persistRoom("older", reader);
        ChatRoom newerRoom = persistRoom("newer", reader);
        persistMember(olderRoom, reader);
        persistMember(newerRoom, reader);

        ChatMessage olderFirstUnread = entityManager.persist(
                ChatMessage.createUserTextMessage(
                        olderRoom,
                        sender,
                        "older unread 1"
                )
        );
        entityManager.persist(ChatMessage.createUserTextMessage(
                olderRoom,
                reader,
                "own message should not count"
        ));
        entityManager.persist(ChatMessage.createSystemMessage(
                olderRoom,
                "system should not be latest conversation preview"
        ));
        ChatMessage olderLatest = entityManager.persist(
                ChatMessage.createUserTextMessage(
                        olderRoom,
                        sender,
                        "older unread 2"
                )
        );

        ChatMessage newerUnread = entityManager.persist(
                ChatMessage.createUserTextMessage(
                        newerRoom,
                        sender,
                        "newer unread"
                )
        );
        entityManager.flush();

        List<ChatNotificationRoomQueryRow> firstPage =
                repository.findUnreadChatRoomPage(
                        reader.getId(),
                        null,
                        1
                );

        assertThat(firstPage).hasSize(1);
        assertThat(firstPage.getFirst().roomId())
                .isEqualTo(newerRoom.getId());
        assertThat(firstPage.getFirst().latestMessageId())
                .isEqualTo(newerUnread.getId());
        assertThat(firstPage.getFirst().firstUnreadMessageId())
                .isEqualTo(newerUnread.getId());
        assertThat(firstPage.getFirst().unreadCount())
                .isEqualTo(1L);

        List<ChatNotificationRoomQueryRow> secondPage =
                repository.findUnreadChatRoomPage(
                        reader.getId(),
                        firstPage.getFirst().latestMessageId(),
                        2
                );

        assertThat(secondPage).hasSize(1);
        ChatNotificationRoomQueryRow olderRow = secondPage.getFirst();
        assertThat(olderRow.roomId()).isEqualTo(olderRoom.getId());
        assertThat(olderRow.latestMessageId())
                .isEqualTo(olderLatest.getId());
        assertThat(olderRow.firstUnreadMessageId())
                .isEqualTo(olderFirstUnread.getId());
        assertThat(olderRow.unreadCount()).isEqualTo(2L);
    }

    @Test
    void summarizesOnlyUnreadConversationMessages() {
        User reader = persistUser(
                "summary-reader@translacat.test",
                "summary-reader",
                "SUMREADER01"
        );
        User sender = persistUser(
                "summary-sender@translacat.test",
                "summary-sender",
                "SUMSENDER01"
        );
        ChatRoom roomOne = persistRoom("room-1", reader);
        ChatRoom roomTwo = persistRoom("room-2", reader);
        persistMember(roomOne, reader);
        persistMember(roomTwo, reader);

        entityManager.persist(ChatMessage.createUserTextMessage(
                roomOne,
                sender,
                "unread 1"
        ));
        entityManager.persist(ChatMessage.createUserTextMessage(
                roomOne,
                sender,
                "unread 2"
        ));
        entityManager.persist(ChatMessage.createUserTextMessage(
                roomOne,
                reader,
                "own"
        ));
        entityManager.persist(ChatMessage.createSystemMessage(
                roomOne,
                "system"
        ));
        entityManager.persist(ChatMessage.createUserTextMessage(
                roomTwo,
                sender,
                "unread 3"
        ));
        entityManager.flush();

        ChatNotificationUnreadSummary summary =
                repository.summarizeUnreadChats(reader.getId());

        assertThat(summary.unreadMessageCount()).isEqualTo(3L);
        assertThat(summary.unreadRoomCount()).isEqualTo(2L);
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

    private ChatRoomMember persistMember(
            ChatRoom room,
            User user
    ) {
        ChatRoomMember member = ChatRoomMember.createMember(
                room,
                user,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(
                member,
                "joinedAt",
                LocalDateTime.now().minusMinutes(1)
        );
        return entityManager.persist(member);
    }
}
