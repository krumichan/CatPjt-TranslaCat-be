package jp.co.translacat.domain.chat.message.repository;

import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.projection.ChatMessageAnchorWindowQueryResult;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:translacat-message-anchor-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class ChatMessageRepositoryImplTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatMessageRepository repository;

    @Test
    void findsOnlyRequestedAnchorWindowAndBothDirectionCursors() {
        User owner = persistUser();
        ChatRoom room = entityManager.persist(
                ChatRoom.createGroupRoom(
                        "anchor-room",
                        null,
                        owner,
                        ChatRoomSourceType.MANUAL
                )
        );
        List<ChatMessage> messages = persistMessages(
                room,
                owner,
                12
        );
        entityManager.flush();

        ChatMessage anchor = messages.get(5);
        ChatMessageAnchorWindowQueryResult result =
                repository.findAnchorWindowIds(
                        room.getId(),
                        anchor.getId(),
                        LocalDateTime.now().minusMinutes(5),
                        2,
                        3
                );

        assertThat(result.messageIds()).containsExactly(
                messages.get(3).getId(),
                messages.get(4).getId(),
                anchor.getId(),
                messages.get(6).getId(),
                messages.get(7).getId(),
                messages.get(8).getId()
        );
        assertThat(result.hasPrevious()).isTrue();
        assertThat(result.previousCursorId())
                .isEqualTo(messages.get(3).getId());
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursorId())
                .isEqualTo(messages.get(8).getId());
    }

    @Test
    void findsForwardPageIdsWithoutLoadingIntermediateHistory() {
        User owner = persistUser();
        ChatRoom room = entityManager.persist(
                ChatRoom.createGroupRoom(
                        "forward-room",
                        null,
                        owner,
                        ChatRoomSourceType.MANUAL
                )
        );
        List<ChatMessage> messages = persistMessages(
                room,
                owner,
                12
        );
        entityManager.flush();

        List<Long> result = repository.findNextMessageIds(
                room.getId(),
                messages.get(5).getId(),
                LocalDateTime.now().minusMinutes(5),
                4
        );

        assertThat(result).containsExactly(
                messages.get(6).getId(),
                messages.get(7).getId(),
                messages.get(8).getId(),
                messages.get(9).getId()
        );
    }

    private List<ChatMessage> persistMessages(
            ChatRoom room,
            User sender,
            int count
    ) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            messages.add(entityManager.persist(
                    ChatMessage.createUserTextMessage(
                            room,
                            sender,
                            "message-" + index
                    )
            ));
        }
        return messages;
    }

    private User persistUser() {
        return entityManager.persist(User.createLocalUser(
                "anchor-owner@translacat.test",
                "password",
                "anchor-owner",
                Role.USER,
                "ANCHOROWN1"
        ));
    }
}
