package jp.co.translacat.domain.chat.openchat.ban.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.ban.entity.OpenChatBan;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberBannedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:open-chat-ban-rollback-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import({
        QueryDslConfig.class,
        OpenChatBanRollbackTest.RollbackEventListener.class
})
class OpenChatBanRollbackTest {

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private OpenChatRoomRepository openChatRoomRepository;
    @Autowired private ChatRoomMemberRepository memberRepository;
    @Autowired private OpenChatMemberProfileRepository profileRepository;
    @Autowired private OpenChatBanRepository banRepository;
    @Autowired private ChatMessageRepository messageRepository;
    @Autowired private RollbackEventListener eventListener;

    @BeforeEach
    void clearEvents() {
        eventListener.clear();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollbackLeavesNoBanMembershipChangeSystemMessageOrEvent() {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> createFixture());
        assertThat(fixture).isNotNull();

        transaction.executeWithoutResult(status -> {
            OpenChatRoom room = openChatRoomRepository
                    .findByChatRoomIdForUpdate(fixture.roomId())
                    .orElseThrow();
            ChatRoomMember actor = memberRepository
                    .findActiveByIdAndRoomIdForUpdate(
                            fixture.ownerMemberId(),
                            fixture.roomId()
                    )
                    .orElseThrow();
            ChatRoomMember target = memberRepository
                    .findActiveByIdAndRoomIdForUpdate(
                            fixture.targetMemberId(),
                            fixture.roomId()
                    )
                    .orElseThrow();
            OpenChatMemberProfile targetProfile = profileRepository
                    .findByChatRoomMemberId(target.getId())
                    .orElseThrow();

            OpenChatBan ban = banRepository.save(OpenChatBan.create(
                    room.getChatRoom(),
                    target.getUser(),
                    target,
                    targetProfile.getMemberCode(),
                    targetProfile.getNickname(),
                    targetProfile.getProfileImageObjectKey(),
                    target.getJoinedAt(),
                    target.getRole(),
                    actor,
                    actor.getRole(),
                    "rollback"
            ));
            target.banFromOpenChat();
            messageRepository.save(ChatMessage.createSystemMessage(
                    room.getChatRoom(),
                    "rollback-system-message"
            ));
            eventPublisher.publishEvent(
                    OpenChatMemberBannedApplicationEvent.of(
                            fixture.roomId(),
                            target.getId(),
                            target.getUser().getEmail(),
                            ban.getReason(),
                            ban.getBannedAt()
                    )
            );

            memberRepository.flush();
            banRepository.flush();
            messageRepository.flush();
            status.setRollbackOnly();
        });

        transaction.executeWithoutResult(status -> {
            assertThat(banRepository.findAll()).isEmpty();
            assertThat(messageRepository.findAll()).isEmpty();
            ChatRoomMember restored = memberRepository
                    .findById(fixture.targetMemberId())
                    .orElseThrow();
            assertThat(restored.isActive()).isTrue();
            assertThat(restored.getRole())
                    .isEqualTo(ChatRoomMemberRole.MEMBER);
        });
        assertThat(eventListener.events()).isEmpty();
    }

    private Fixture createFixture() {
        User owner = userRepository.save(user(
                "rollback-owner@open.test",
                "ROLLBACKO1"
        ));
        User target = userRepository.save(user(
                "rollback-target@open.test",
                "ROLLBACKT1"
        ));
        ChatRoom room = chatRoomRepository.save(
                ChatRoom.createOpenRoom("rollback", "desc", owner)
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
                "OC-TARGET",
                "대상고양이",
                null
        ));
        memberRepository.flush();
        return new Fixture(
                room.getId(),
                ownerMember.getId(),
                targetMember.getId()
        );
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
            Long targetMemberId
    ) {
    }

    @Component
    public static class RollbackEventListener {
        private final List<OpenChatMemberBannedApplicationEvent> events =
                new ArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void handle(OpenChatMemberBannedApplicationEvent event) {
            events.add(event);
        }

        List<OpenChatMemberBannedApplicationEvent> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }
}
