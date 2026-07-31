package jp.co.translacat.domain.chat.read.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
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
        ChatMessageUnreadMemberCountRepositoryImpl.class
})
class ChatMessageUnreadMemberCountRepositoryImplTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatMessageUnreadMemberCountRepository repository;


    @Test
    void directMessageCountChangesFromOneToZero() {
        User sender = persistUser(
                "direct-sender@translacat.test",
                "direct-sender",
                "DIRECTSEND1"
        );
        User reader = persistUser(
                "direct-reader@translacat.test",
                "direct-reader",
                "DIRECTREAD1"
        );
        ChatRoom chatRoom = persistRoom(sender);
        persistMember(chatRoom, sender);
        ChatRoomMember readerMember = persistMember(chatRoom, reader);

        ChatMessage message = entityManager.persist(
                ChatMessage.createUserTextMessage(
                        chatRoom,
                        sender,
                        "direct message"
                )
        );
        entityManager.flush();

        assertThat(count(message.getId())).isEqualTo(1L);

        readerMember.advanceReadCursor(message.getId());
        entityManager.flush();

        assertThat(count(message.getId())).isZero();
    }

    @Test
    void groupMessageCountDecreasesFromTwoToZero() {
        User sender = persistUser(
                "count-sender@translacat.test",
                "count-sender",
                "COUNTSEND01"
        );
        User readerOne = persistUser(
                "count-reader1@translacat.test",
                "count-reader1",
                "COUNTREAD01"
        );
        User readerTwo = persistUser(
                "count-reader2@translacat.test",
                "count-reader2",
                "COUNTREAD02"
        );
        ChatRoom chatRoom = persistRoom(sender);

        persistMember(chatRoom, sender);
        ChatRoomMember readerOneMember =
                persistMember(chatRoom, readerOne);
        ChatRoomMember readerTwoMember =
                persistMember(chatRoom, readerTwo);

        ChatMessage message = entityManager.persist(
                ChatMessage.createUserTextMessage(
                        chatRoom,
                        sender,
                        "group message"
                )
        );
        entityManager.flush();

        assertThat(count(message.getId())).isEqualTo(2L);

        readerOneMember.advanceReadCursor(message.getId());
        entityManager.flush();
        assertThat(count(message.getId())).isEqualTo(1L);

        readerTwoMember.advanceReadCursor(message.getId());
        entityManager.flush();
        assertThat(count(message.getId())).isZero();
    }

    @Test
    void excludesSenderLateJoinerInactiveMemberAndSystemMessage() {
        User sender = persistUser(
                "exclude-sender@translacat.test",
                "exclude-sender",
                "EXCLSENDER1"
        );
        User reader = persistUser(
                "exclude-reader@translacat.test",
                "exclude-reader",
                "EXCLREADER1"
        );
        User lateJoiner = persistUser(
                "exclude-late@translacat.test",
                "exclude-late",
                "EXCLLATE01"
        );
        User inactiveUser = persistUser(
                "exclude-inactive@translacat.test",
                "exclude-inactive",
                "EXCLINACT1"
        );
        ChatRoom chatRoom = persistRoom(sender);

        persistMember(chatRoom, sender);
        persistMember(chatRoom, reader);
        ChatRoomMember inactiveMember =
                persistMember(chatRoom, inactiveUser);
        inactiveMember.leave();

        ChatMessage userMessage = entityManager.persist(
                ChatMessage.createUserTextMessage(
                        chatRoom,
                        sender,
                        "before late join"
                )
        );
        ChatMessage systemMessage = entityManager.persist(
                ChatMessage.createSystemMessage(
                        chatRoom,
                        "system"
                )
        );
        entityManager.flush();

        ChatRoomMember lateMember = ChatRoomMember.createMember(
                chatRoom,
                lateJoiner,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(
                lateMember,
                "joinedAt",
                userMessage.getCreatedAt().plusSeconds(1)
        );
        entityManager.persist(lateMember);
        entityManager.flush();

        Map<Long, Long> counts =
                repository.countUnreadMembersByMessageIds(
                        List.of(
                                userMessage.getId(),
                                systemMessage.getId()
                        )
                );

        assertThat(counts.get(userMessage.getId())).isEqualTo(1L);
        assertThat(counts).doesNotContainKey(systemMessage.getId());
    }

    @Test
    void aiMessageCountsAllActiveHumanMembers() {
        User first = persistUser(
                "ai-first@translacat.test",
                "ai-first",
                "AIFIRST001"
        );
        User second = persistUser(
                "ai-second@translacat.test",
                "ai-second",
                "AISECOND01"
        );
        ChatRoom chatRoom = persistRoom(first);
        persistMember(chatRoom, first);
        persistMember(chatRoom, second);

        ChatMessage aiMessage = ChatMessage.createUserTextMessage(
                chatRoom,
                first,
                "ai message"
        );
        ReflectionTestUtils.setField(aiMessage, "senderUser", null);
        ReflectionTestUtils.setField(
                aiMessage,
                "senderType",
                ChatMessageSenderType.AI
        );
        entityManager.persist(aiMessage);
        entityManager.flush();

        assertThat(count(aiMessage.getId())).isEqualTo(2L);
    }

    private long count(Long messageId) {
        return repository.countUnreadMembers(messageId);
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
                "message-read-count-room",
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
}
