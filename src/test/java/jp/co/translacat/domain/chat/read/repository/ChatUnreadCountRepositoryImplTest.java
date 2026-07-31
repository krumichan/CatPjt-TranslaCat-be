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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:translacat-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"
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

        ChatMessage beforeMessage = entityManager.persist(
                ChatMessage.createUserTextMessage(
                        chatRoom,
                        sender,
                        "before join"
                )
        );

        ChatRoomMember member = persistMember(
                chatRoom,
                reader
        );

        ChatMessage afterMessage = entityManager.persist(
                ChatMessage.createUserTextMessage(
                        chatRoom,
                        sender,
                        "after join"
                )
        );

        entityManager.flush();

        /*
         * Hibernate/H2가 자동 생성하는 현재 시각에 의존하지 않고,
         * 테스트에서 사용할 시간 순서를 명시적으로 고정한다.
         *
         * beforeMessage.createdAt
         * <
         * member.joinedAt
         * <
         * afterMessage.createdAt
         */
        LocalDateTime beforeCreatedAt =
                LocalDateTime.of(2026, 1, 1, 10, 0, 0);

        LocalDateTime joinedAt =
                LocalDateTime.of(2026, 1, 1, 10, 1, 0);

        LocalDateTime afterCreatedAt =
                LocalDateTime.of(2026, 1, 1, 10, 2, 0);

        updateMessageCreatedAt(
                beforeMessage.getId(),
                beforeCreatedAt
        );
        updateMemberJoinedAt(
                member.getId(),
                joinedAt
        );
        updateMessageCreatedAt(
                afterMessage.getId(),
                afterCreatedAt
        );

        /*
         * Native Update는 현재 영속성 Context에 반영되지 않으므로
         * 기존 Entity Cache를 비우고 DB에서 다시 조회해야 한다.
         */
        entityManager.clear();

        ChatMessage savedBeforeMessage =
                entityManager.find(
                        ChatMessage.class,
                        beforeMessage.getId()
                );

        ChatRoomMember savedMember =
                entityManager.find(
                        ChatRoomMember.class,
                        member.getId()
                );

        ChatMessage savedAfterMessage =
                entityManager.find(
                        ChatMessage.class,
                        afterMessage.getId()
                );

        assertThat(savedBeforeMessage.getCreatedAt())
                .isBefore(savedMember.getJoinedAt());

        assertThat(savedAfterMessage.getCreatedAt())
                .isAfter(savedMember.getJoinedAt());

        Map<Long, Long> unread =
                chatUnreadCountRepository.countUnreadByRoomIds(
                        reader.getId(),
                        List.of(chatRoom.getId())
                );

        /*
         * beforeMessage는 joinedAt 이전이므로 제외되고,
         * afterMessage만 미읽음으로 집계되어야 한다.
         */
        assertThat(unread.get(chatRoom.getId()))
                .isEqualTo(1L);
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
        ReflectionTestUtils.setField(
                member,
                "joinedAt",
                LocalDateTime.now().minusSeconds(1)
        );
        return entityManager.persist(member);
    }

    private void updateMessageCreatedAt(
            Long messageId,
            LocalDateTime createdAt
    ) {
        int updatedCount = entityManager
                .getEntityManager()
                .createNativeQuery("""
                    UPDATE chat_message
                       SET created_at = :createdAt
                     WHERE id = :messageId
                    """)
                .setParameter("createdAt", createdAt)
                .setParameter("messageId", messageId)
                .executeUpdate();

        assertThat(updatedCount).isEqualTo(1);
    }

    private void updateMemberJoinedAt(
            Long memberId,
            LocalDateTime joinedAt
    ) {
        int updatedCount = entityManager
                .getEntityManager()
                .createNativeQuery("""
                    UPDATE chat_room_member
                       SET joined_at = :joinedAt
                     WHERE id = :memberId
                    """)
                .setParameter("joinedAt", joinedAt)
                .setParameter("memberId", memberId)
                .executeUpdate();

        assertThat(updatedCount).isEqualTo(1);
    }
}
