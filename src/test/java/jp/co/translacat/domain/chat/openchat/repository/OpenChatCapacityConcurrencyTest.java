package jp.co.translacat.domain.chat.openchat.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
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
        "spring.datasource.url=jdbc:h2:mem:open-chat-capacity-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER",
        "spring.datasource.hikari.maximum-pool-size=4"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class OpenChatCapacityConcurrencyTest {

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private OpenChatRoomRepository openChatRoomRepository;
    @Autowired private ChatRoomMemberRepository memberRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onlyOneConcurrentJoinWinsTheLastCapacitySlot() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );
        Fixture fixture = transaction.execute(status -> createFixture());
        assertThat(fixture).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> joinWithLock(
                    transaction,
                    fixture.roomId(),
                    fixture.firstUserId(),
                    ready,
                    start
            ));
            Future<Boolean> second = executor.submit(() -> joinWithLock(
                    transaction,
                    fixture.roomId(),
                    fixture.secondUserId(),
                    ready,
                    start
            ));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            ))
                    .containsExactlyInAnyOrder(true, false);

            Long finalCount = transaction.execute(status ->
                    memberRepository
                            .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                                    fixture.roomId()
                            )
            );
            assertThat(finalCount).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture createFixture() {
        User owner = userRepository.save(user(
                "capacity-owner@open.test",
                "CAPOWNER01"
        ));
        User first = userRepository.save(user(
                "capacity-first@open.test",
                "CAPFIRST01"
        ));
        User second = userRepository.save(user(
                "capacity-second@open.test",
                "CAPSECOND1"
        ));
        ChatRoom room = chatRoomRepository.save(
                ChatRoom.createOpenRoom(
                        "capacity-room",
                        "desc",
                        owner
                )
        );
        openChatRoomRepository.save(OpenChatRoom.create(
                room,
                OpenChatVisibility.PUBLIC,
                2
        ));
        memberRepository.saveAndFlush(ChatRoomMember.createOwner(
                room,
                owner,
                "ko",
                "ja"
        ));
        openChatRoomRepository.flush();

        return new Fixture(
                room.getId(),
                first.getId(),
                second.getId()
        );
    }

    private boolean joinWithLock(
            TransactionTemplate transaction,
            Long roomId,
            Long userId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();

        Boolean result = transaction.execute(status -> {
            OpenChatRoom openRoom = openChatRoomRepository
                    .findByChatRoomIdForUpdate(roomId)
                    .orElseThrow();
            long currentCount = memberRepository
                    .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                            roomId
                    );
            if (currentCount >= openRoom.getMaxMemberCount()) {
                return false;
            }

            User user = userRepository.findById(userId).orElseThrow();
            ChatRoom room = chatRoomRepository.findById(roomId)
                    .orElseThrow();
            memberRepository.saveAndFlush(ChatRoomMember.createMember(
                    room,
                    user,
                    "ko",
                    "ja"
            ));
            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    private User user(String email, String publicId) {
        return User.createLocalUser(
                email,
                "password",
                email,
                Role.USER,
                publicId
        );
    }

    private record Fixture(
            Long roomId,
            Long firstUserId,
            Long secondUserId
    ) {
    }
}
