package jp.co.translacat.domain.chat.openchat.ban.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.openchat.ban.entity.OpenChatBan;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
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
        "spring.datasource.url=jdbc:h2:mem:open-chat-ban-concurrency-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER",
        "spring.datasource.hikari.maximum-pool-size=4"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class OpenChatBanConcurrencyTest {

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private OpenChatRoomRepository openChatRoomRepository;
    @Autowired private OpenChatMemberProfileRepository profileRepository;
    @Autowired private ChatRoomMemberRepository memberRepository;
    @Autowired private OpenChatBanRepository banRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onlyOneConcurrentActiveBanIsCreatedForSameRoomAndUser()
            throws Exception {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> createFixture());
        assertThat(fixture).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> banWithRoomLock(
                    transaction,
                    fixture,
                    ready,
                    start
            ));
            Future<Boolean> second = executor.submit(() -> banWithRoomLock(
                    transaction,
                    fixture,
                    ready,
                    start
            ));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(true, false);

            Long activeCount = transaction.execute(status ->
                    (long) banRepository.findActivePage(
                            fixture.roomId(),
                            null,
                            null,
                            10
                    ).size()
            );
            assertThat(activeCount).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture createFixture() {
        User owner = userRepository.save(user(
                "ban-concurrent-owner@open.test",
                "BANCONOWN1"
        ));
        User target = userRepository.save(user(
                "ban-concurrent-target@open.test",
                "BANCONTGT1"
        ));
        ChatRoom room = chatRoomRepository.save(
                ChatRoom.createOpenRoom(
                        "ban-concurrency",
                        "desc",
                        owner
                )
        );
        openChatRoomRepository.save(OpenChatRoom.create(
                room,
                OpenChatVisibility.PUBLIC,
                50
        ));
        ChatRoomMember ownerMember = memberRepository.save(
                ChatRoomMember.createOwner(
                        room,
                        owner,
                        "ko",
                        "ja"
                )
        );
        ChatRoomMember targetMember = memberRepository.save(
                ChatRoomMember.createMember(
                        room,
                        target,
                        "ko",
                        "ja"
                )
        );
        profileRepository.save(OpenChatMemberProfile.create(
                ownerMember,
                "OC-OWNER",
                "운영고양이",
                null
        ));
        profileRepository.save(OpenChatMemberProfile.create(
                targetMember,
                "OC-CONCUR",
                "같은고양이",
                null
        ));
        memberRepository.flush();
        openChatRoomRepository.flush();

        return new Fixture(
                room.getId(),
                ownerMember.getId(),
                targetMember.getId(),
                target.getId()
        );
    }

    private boolean banWithRoomLock(
            TransactionTemplate transaction,
            Fixture fixture,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();

        Boolean result = transaction.execute(status -> {
            OpenChatRoom openRoom = openChatRoomRepository
                    .findByChatRoomIdForUpdate(fixture.roomId())
                    .orElseThrow();

            if (banRepository
                    .findActiveByRoomIdAndTargetUserIdForUpdate(
                            fixture.roomId(),
                            fixture.targetUserId()
                    )
                    .isPresent()) {
                return false;
            }

            ChatRoomMember actor = memberRepository
                    .findById(fixture.ownerMemberId())
                    .orElseThrow();
            ChatRoomMember target = memberRepository
                    .findById(fixture.targetMemberId())
                    .orElseThrow();

            banRepository.saveAndFlush(OpenChatBan.create(
                    openRoom.getChatRoom(),
                    target.getUser(),
                    target,
                    "OC-CONCUR",
                    "같은고양이",
                    null,
                    target.getJoinedAt(),
                    ChatRoomMemberRole.MEMBER,
                    actor,
                    ChatRoomMemberRole.OWNER,
                    "reason"
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
            Long ownerMemberId,
            Long targetMemberId,
            Long targetUserId
    ) {
    }
}
