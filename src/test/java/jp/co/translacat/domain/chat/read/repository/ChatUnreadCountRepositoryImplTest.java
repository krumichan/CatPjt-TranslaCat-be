package jp.co.translacat.domain.chat.read.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-read-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import({
        QueryDslConfig.class,
        ChatUnreadCountRepositoryImpl.class
})
class ChatUnreadCountRepositoryImplTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatUnreadCountRepository chatUnreadCountRepository;

    @Test
    void countsOnlyOtherUsersNormalMessagesAfterReadCursor() {
        User reader = persistUser(
                "reader-count@translacat.test",
                "reader-count",
                "READCOUNT01"
        );
        User sender = persistUser(
                "sender-count@translacat.test",
                "sender-count",
                "SENDCOUNT01"
        );
        ChatRoom chatRoom = persistRoom(reader);
        ChatRoomMember member = persistMember(chatRoom, reader);

        ChatMessage otherUserMessage = entityManager.persist(
                ChatMessage.createUserTextMessage(
                        chatRoom,
                        sender,
                        "other user message"
                )
        );
        entityManager.persist(
                ChatMessage.createUserTextMessage(
                        chatRoom,
                        reader,
                        "own message"
                )
        );
        entityManager.persist(
                ChatMessage.createSystemMessage(
                        chatRoom,
                        "system message"
                )
        );
        entityManager.flush();

        Map<Long, Long> unreadBeforeRead =
                chatUnreadCountRepository.countUnreadByRoomIds(
                        reader.getId(),
                        List.of(chatRoom.getId())
                );
        assertThat(unreadBeforeRead.get(chatRoom.getId())).isEqualTo(1L);

        member.advanceReadCursor(otherUserMessage.getId());
        entityManager.flush();

        Map<Long, Long> unreadAfterRead =
                chatUnreadCountRepository.countUnreadByRoomIds(
                        reader.getId(),
                        List.of(chatRoom.getId())
                );
        assertThat(unreadAfterRead)
                .doesNotContainKey(chatRoom.getId());
    }

    @Test
    void excludesMessagesCreatedBeforeLatestJoinedAt() {
        User reader = persistUser(
                "reader-join@translacat.test",
                "reader-join",
                "READJOIN001"
        );
        User sender = persistUser(
                "sender-join@translacat.test",
                "sender-join",
                "SENDJOIN001"
        );
        ChatRoom chatRoom = persistRoom(reader);

        entityManager.persist(
                ChatMessage.createUserTextMessage(
                        chatRoom,
                        sender,
                        "before join"
                )
        );
        entityManager.flush();

        persistMember(chatRoom, reader);

        entityManager.persist(
                ChatMessage.createUserTextMessage(
                        chatRoom,
                        sender,
                        "after join"
                )
        );
        entityManager.flush();

        Map<Long, Long> unread =
                chatUnreadCountRepository.countUnreadByRoomIds(
                        reader.getId(),
                        List.of(chatRoom.getId())
                );

        assertThat(unread.get(chatRoom.getId())).isEqualTo(1L);
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

    private ChatRoom persistRoom(User owner) {
        return entityManager.persist(ChatRoom.createGroupRoom(
                "read-test-room",
                null,
                owner,
                ChatRoomSourceType.MANUAL
        ));
    }

    private ChatRoomMember persistMember(
            ChatRoom chatRoom,
            User user
    ) {
        ChatRoomMember member = ChatRoomMember.createMember(
                chatRoom,
                user,
                "ko",
                "ja"
        );
        return entityManager.persist(member);
    }
}
