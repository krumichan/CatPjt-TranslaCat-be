package jp.co.translacat.domain.chat.openchat.ban.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.openchat.ban.entity.OpenChatBan;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:open-chat-ban-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class OpenChatBanRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OpenChatBanRepository banRepository;

    @Test
    void storesSnapshotSearchesByNicknameAndMemberCodeAndKeepsHistory() {
        Fixture fixture = fixture();

        OpenChatBan first = entityManager.persist(OpenChatBan.create(
                fixture.room(),
                fixture.targetUser(),
                fixture.targetMember(),
                "OC-TARGET",
                "같은고양이",
                "snapshot/avatar.png",
                fixture.targetMember().getJoinedAt(),
                ChatRoomMemberRole.MEMBER,
                fixture.ownerMember(),
                ChatRoomMemberRole.OWNER,
                "first"
        ));
        entityManager.flush();
        entityManager.clear();

        assertThat(banRepository
                .existsActiveByRoomIdAndTargetUserId(
                        fixture.room().getId(),
                        fixture.targetUser().getId()
                ))
                .isTrue();
        assertThat(banRepository.findActivePage(
                fixture.room().getId(),
                "같은",
                null,
                10
        )).extracting(OpenChatBanQueryRow::memberCode)
                .containsExactly("OC-TARGET");
        assertThat(banRepository.findActivePage(
                fixture.room().getId(),
                "TARGET",
                null,
                10
        )).hasSize(1);

        OpenChatBan locked = banRepository
                .findActiveByIdAndRoomIdForUpdate(
                        first.getId(),
                        fixture.room().getId()
                )
                .orElseThrow();
        ChatRoom managedRoom = locked.getChatRoom();
        User managedTargetUser = locked.getTargetUser();
        ChatRoomMember managedTargetMember =
                locked.getTargetChatRoomMember();
        ChatRoomMember managedOwnerMember =
                locked.getBannedByMember();

        locked.release(managedOwnerMember);
        banRepository.flush();

        OpenChatBan second = entityManager.persist(OpenChatBan.create(
                managedRoom,
                managedTargetUser,
                managedTargetMember,
                "OC-TARGET",
                "같은고양이",
                "snapshot/avatar-2.png",
                managedTargetMember.getJoinedAt(),
                ChatRoomMemberRole.MEMBER,
                managedOwnerMember,
                ChatRoomMemberRole.OWNER,
                "second"
        ));
        entityManager.flush();

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(banRepository.findAll()).hasSize(2);
        assertThat(banRepository.findActivePage(
                fixture.room().getId(),
                null,
                null,
                10
        )).extracting(OpenChatBanQueryRow::reason)
                .containsExactly("second");
    }

    @Test
    void distinguishesTenIdenticalProfilesByMemberCode() {
        User owner = persistUser(
                "owner-ten@open.test",
                "OWNER10TEST"
        );
        ChatRoom room = entityManager.persist(
                ChatRoom.createOpenRoom("open", "desc", owner)
        );
        entityManager.persist(OpenChatRoom.create(
                room,
                OpenChatVisibility.PUBLIC,
                50
        ));
        ChatRoomMember ownerMember = entityManager.persist(
                ChatRoomMember.createOwner(
                        room,
                        owner,
                        "ko",
                        "ja"
                )
        );
        entityManager.persist(OpenChatMemberProfile.create(
                ownerMember,
                "OC-OWNER",
                "운영고양이",
                null
        ));

        for (int index = 0; index < 10; index++) {
            User target = persistUser(
                    "same-" + index + "@open.test",
                    "SAMEUSER" + index
            );
            ChatRoomMember member = entityManager.persist(
                    ChatRoomMember.createMember(
                            room,
                            target,
                            "ko",
                            "ja"
                    )
            );
            String memberCode = "OC-SAME" + index;
            entityManager.persist(OpenChatMemberProfile.create(
                    member,
                    memberCode,
                    "같은고양이",
                    null
            ));
            entityManager.persist(OpenChatBan.create(
                    room,
                    target,
                    member,
                    memberCode,
                    "같은고양이",
                    null,
                    member.getJoinedAt(),
                    ChatRoomMemberRole.MEMBER,
                    ownerMember,
                    ChatRoomMemberRole.OWNER,
                    "reason"
            ));
        }
        entityManager.flush();
        entityManager.clear();

        List<OpenChatBanQueryRow> rows = banRepository.findActivePage(
                room.getId(),
                "같은고양이",
                null,
                20
        );

        assertThat(rows).hasSize(10);
        assertThat(rows)
                .extracting(OpenChatBanQueryRow::memberCode)
                .containsExactlyInAnyOrderElementsOf(
                        Set.of(
                                "OC-SAME0",
                                "OC-SAME1",
                                "OC-SAME2",
                                "OC-SAME3",
                                "OC-SAME4",
                                "OC-SAME5",
                                "OC-SAME6",
                                "OC-SAME7",
                                "OC-SAME8",
                                "OC-SAME9"
                        )
                );
    }

    private Fixture fixture() {
        User owner = persistUser(
                "owner@ban.test",
                "BANOWNER01"
        );
        User target = persistUser(
                "target@ban.test",
                "BANTARGET1"
        );
        ChatRoom room = entityManager.persist(
                ChatRoom.createOpenRoom("open", "desc", owner)
        );
        entityManager.persist(OpenChatRoom.create(
                room,
                OpenChatVisibility.PUBLIC,
                50
        ));
        ChatRoomMember ownerMember = entityManager.persist(
                ChatRoomMember.createOwner(
                        room,
                        owner,
                        "ko",
                        "ja"
                )
        );
        ChatRoomMember targetMember = entityManager.persist(
                ChatRoomMember.createMember(
                        room,
                        target,
                        "ko",
                        "ja"
                )
        );
        entityManager.persist(OpenChatMemberProfile.create(
                ownerMember,
                "OC-OWNER",
                "운영고양이",
                null
        ));
        entityManager.persist(OpenChatMemberProfile.create(
                targetMember,
                "OC-TARGET",
                "같은고양이",
                null
        ));
        entityManager.flush();
        return new Fixture(
                room,
                ownerMember,
                target,
                targetMember
        );
    }

    private User persistUser(String email, String publicId) {
        return entityManager.persist(User.createLocalUser(
                email,
                "password",
                email,
                Role.USER,
                publicId
        ));
    }

    private record Fixture(
            ChatRoom room,
            ChatRoomMember ownerMember,
            User targetUser,
            ChatRoomMember targetMember
    ) {
    }
}
