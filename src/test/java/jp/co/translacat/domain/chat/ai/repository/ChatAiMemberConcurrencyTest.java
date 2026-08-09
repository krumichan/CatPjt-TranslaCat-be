package jp.co.translacat.domain.chat.ai.repository;

import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-ai-capacity-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER",
        "spring.datasource.hikari.maximum-pool-size=4"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class ChatAiMemberConcurrencyTest {

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatAiAgentRepository agentRepository;
    @Autowired private ChatRoomAiMemberRepository aiMemberRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void roomLockAllowsOnlyOneConcurrentInsertForLastAiSlot() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Long roomId = transaction.execute(status -> createFixture());
        assertThat(roomId).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> addWithRoomLock(
                    transaction,
                    roomId,
                    "first",
                    ready,
                    start
            ));
            Future<Boolean> second = executor.submit(() -> addWithRoomLock(
                    transaction,
                    roomId,
                    "second",
                    ready,
                    start
            ));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(true, false);

            Long finalCount = transaction.execute(status ->
                    aiMemberRepository
                            .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(roomId)
            );
            assertThat(finalCount).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    private Long createFixture() {
        User owner = userRepository.save(User.createLocalUser(
                "ai-capacity-owner@test.local",
                "password",
                "owner",
                Role.USER,
                "AICAPOWNER"
        ));
        ChatRoom room = chatRoomRepository.save(
                ChatRoom.createGroupRoom("group", "desc", owner)
        );
        ChatAiAgent existingAgent = agentRepository.save(
                ChatAiAgent.create("existing", null, "ko", "persona")
        );
        aiMemberRepository.saveAndFlush(
                ChatRoomAiMember.create(room, existingAgent)
        );
        return room.getId();
    }

    private boolean addWithRoomLock(
            TransactionTemplate transaction,
            Long roomId,
            String suffix,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();

        Boolean result = transaction.execute(status -> {
            ChatRoom room = chatRoomRepository
                    .findActiveByIdForUpdate(roomId)
                    .orElseThrow();
            long count = aiMemberRepository
                    .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(roomId);
            if (count >= 2) {
                return false;
            }

            ChatAiAgent agent = agentRepository.save(
                    ChatAiAgent.create(
                            "ai-" + suffix,
                            null,
                            "ja",
                            "persona"
                    )
            );
            aiMemberRepository.saveAndFlush(
                    ChatRoomAiMember.create(room, agent)
            );
            return true;
        });
        return Boolean.TRUE.equals(result);
    }
}
