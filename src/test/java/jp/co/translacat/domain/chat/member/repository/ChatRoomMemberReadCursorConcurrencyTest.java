package jp.co.translacat.domain.chat.member.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:translacat-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatRoomMemberReadCursorConcurrencyTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    @DisplayName("동시 읽음 요청에서도 가장 큰 메시지 ID를 유지한다")
    void keepsGreatestReadCursorForConcurrentRequests() throws Exception {
        // given
        TestIds ids = createTestData();
        CountDownLatch highCursorLockAcquired = new CountDownLatch(1);
        CountDownLatch allowHighCursorCommit = new CountDownLatch(1);
        CountDownLatch lowCursorAttemptStarted = new CountDownLatch(1);

        Future<Boolean> highCursorFuture = executorService.submit(() ->
                executeInTransaction(() -> {
                    ChatRoomMember member = chatRoomMemberRepository
                            .findActiveByRoomIdAndUserIdForUpdate(
                                    ids.chatRoomId(),
                                    ids.userId()
                            )
                            .orElseThrow();

                    boolean advanced = member.advanceReadCursor(200L);
                    chatRoomMemberRepository.saveAndFlush(member);
                    highCursorLockAcquired.countDown();
                    await(allowHighCursorCommit);
                    return advanced;
                })
        );

        assertThat(highCursorLockAcquired.await(5, TimeUnit.SECONDS))
                .isTrue();

        Future<Boolean> lowCursorFuture = executorService.submit(() ->
                executeInTransaction(() -> {
                    lowCursorAttemptStarted.countDown();
                    ChatRoomMember member = chatRoomMemberRepository
                            .findActiveByRoomIdAndUserIdForUpdate(
                                    ids.chatRoomId(),
                                    ids.userId()
                            )
                            .orElseThrow();

                    boolean advanced = member.advanceReadCursor(100L);
                    if (advanced) {
                        chatRoomMemberRepository.saveAndFlush(member);
                    }
                    return advanced;
                })
        );

        assertThat(lowCursorAttemptStarted.await(5, TimeUnit.SECONDS))
                .isTrue();

        // 낮은 커서 요청은 높은 커서 트랜잭션이 보유한 행 Lock을 기다려야 한다.
        Thread.sleep(200L);
        assertThat(lowCursorFuture.isDone()).isFalse();

        allowHighCursorCommit.countDown();

        // when
        assertThat(highCursorFuture.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(lowCursorFuture.get(10, TimeUnit.SECONDS)).isFalse();

        Long finalCursor = executeInTransaction(() ->
                chatRoomMemberRepository.findById(ids.memberId())
                        .orElseThrow()
                        .getLastReadMessageId()
        );

        // then
        assertThat(finalCursor).isEqualTo(200L);
    }

    private TestIds createTestData() {
        return executeInTransaction(() -> {
            User user = userRepository.saveAndFlush(
                    User.createLocalUser(
                            "read-concurrency@translacat.test",
                            "password",
                            "readConcurrencyUser",
                            Role.USER,
                            "READCONCUR01"
                    )
            );

            ChatRoom chatRoom = chatRoomRepository.saveAndFlush(
                    ChatRoom.createGroupRoom(
                            "read-concurrency-room",
                            null,
                            user,
                            ChatRoomSourceType.MANUAL
                    )
            );

            ChatRoomMember member = chatRoomMemberRepository.saveAndFlush(
                    ChatRoomMember.createMember(
                            chatRoom,
                            user,
                            "ko",
                            "ja"
                    )
            );

            return new TestIds(
                    user.getId(),
                    chatRoom.getId(),
                    member.getId()
            );
        });
    }

    private <T> T executeInTransaction(
            java.util.concurrent.Callable<T> callback
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        return transactionTemplate.execute(status -> {
            try {
                return callback.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "동시성 테스트 대기 시간이 초과되었습니다."
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record TestIds(
            Long userId,
            Long chatRoomId,
            Long memberId
    ) {
    }
}
