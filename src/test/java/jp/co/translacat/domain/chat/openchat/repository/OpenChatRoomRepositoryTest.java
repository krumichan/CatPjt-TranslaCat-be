package jp.co.translacat.domain.chat.openchat.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:open-chat-room-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class OpenChatRoomRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OpenChatRoomRepository openChatRoomRepository;

    @Test
    @DisplayName("PUBLIC ACTIVE 방만 목록·검색에 노출한다")
    void findPublicActiveRooms() {
        User publicOwner = persistUser(
                "public-owner@example.com",
                "PUBLICOWNER1"
        );
        User unlistedOwner = persistUser(
                "unlisted-owner@example.com",
                "UNLISTOWNER1"
        );
        User closedOwner = persistUser(
                "closed-owner@example.com",
                "CLOSEDOWNER1"
        );

        OpenChatRoom publicRoom = persistOpenRoom(
                publicOwner,
                "일본어 고양이 대화방",
                "일본어 회화 연습",
                OpenChatVisibility.PUBLIC,
                "OC-PUB01"
        );
        OpenChatRoom unlistedRoom = persistOpenRoom(
                unlistedOwner,
                "링크 전용 고양이방",
                "검색되면 안 됩니다.",
                OpenChatVisibility.UNLISTED,
                "OC-UNL01"
        );
        OpenChatRoom closedRoom = persistOpenRoom(
                closedOwner,
                "종료된 공개방",
                "검색되면 안 됩니다.",
                OpenChatVisibility.PUBLIC,
                "OC-CLS01"
        );
        closedRoom.close();
        entityManager.flush();

        List<OpenChatRoom> allPublic =
                openChatRoomRepository.findPublicActivePage(
                        null,
                        null,
                        10
                );
        List<OpenChatRoom> searched =
                openChatRoomRepository.findPublicActivePage(
                        "회화",
                        null,
                        10
                );

        assertThat(allPublic)
                .extracting(room -> room.getChatRoom().getId())
                .containsExactly(publicRoom.getChatRoom().getId());
        assertThat(searched)
                .extracting(room -> room.getChatRoom().getId())
                .containsExactly(publicRoom.getChatRoom().getId());
        assertThat(openChatRoomRepository.findByChatRoomId(
                unlistedRoom.getChatRoom().getId()
        )).isPresent();
        assertThat(openChatRoomRepository.findByChatRoomId(
                closedRoom.getChatRoom().getId()
        )).isPresent();

        assertThat(openChatRoomRepository.findActiveRoomIds(List.of(
                publicRoom.getChatRoom().getId(),
                unlistedRoom.getChatRoom().getId(),
                closedRoom.getChatRoom().getId()
        ))).containsExactlyInAnyOrder(
                publicRoom.getChatRoom().getId(),
                unlistedRoom.getChatRoom().getId()
        );
    }

    @Test
    @DisplayName("멤버 수·참여 상태·OWNER 프로필·최근 활동을 일괄 조회한다")
    void loadListMetadataWithoutRowQueries() {
        User owner = persistUser(
                "metadata-owner@example.com",
                "METAOWNER01"
        );
        User viewer = persistUser(
                "metadata-viewer@example.com",
                "METAVIEWER1"
        );
        OpenChatRoom room = persistOpenRoom(
                owner,
                "메타데이터 공개방",
                "설명",
                OpenChatVisibility.PUBLIC,
                "OC-META1"
        );
        ChatRoomMember viewerMember = ChatRoomMember.createMember(
                room.getChatRoom(),
                viewer,
                "ko",
                "ja"
        );
        entityManager.persist(viewerMember);
        ChatMessage message = entityManager.persist(
                ChatMessage.createUserTextMessage(
                        room.getChatRoom(),
                        owner,
                        "최근 메시지"
                )
        );
        entityManager.flush();

        Long roomId = room.getChatRoom().getId();
        List<Long> roomIds = List.of(roomId);
        Map<Long, Long> counts =
                openChatRoomRepository.countActiveMembers(roomIds);
        Set<Long> joined = openChatRoomRepository.findJoinedRoomIds(
                viewer.getId(),
                roomIds
        );
        Map<Long, OpenChatMemberProfileQueryRow> owners =
                openChatRoomRepository.findOwnerProfiles(roomIds);
        Map<Long, LocalDateTime> lastActivity =
                openChatRoomRepository.findLastActivityAt(roomIds);

        assertThat(counts.get(roomId)).isEqualTo(2L);
        assertThat(joined).containsExactly(roomId);
        assertThat(owners.get(roomId).memberCode())
                .isEqualTo("OC-META1");
        assertThat(owners.get(roomId).nickname())
                .isEqualTo("방장고양이");
        assertThat(lastActivity.get(roomId))
                .isEqualTo(message.getCreatedAt());
        assertThat(openChatRoomRepository.findMyProfile(
                roomId,
                viewer.getId()
        )).isEmpty();
    }

    @Test
    @DisplayName("OPEN 방을 비관적 쓰기 잠금 대상으로 조회한다")
    void findByChatRoomIdForUpdate() {
        User owner = persistUser(
                "lock-owner@example.com",
                "LOCKOWNER01"
        );
        OpenChatRoom room = persistOpenRoom(
                owner,
                "정원 잠금 테스트방",
                "설명",
                OpenChatVisibility.PUBLIC,
                "OC-LOCK1"
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(openChatRoomRepository.findByChatRoomIdForUpdate(
                room.getChatRoom().getId()
        )).isPresent();
    }

    @Test
    @DisplayName("일반 퇴실 후에도 방별 프로필과 memberCode를 재참여용으로 조회한다")
    void findMyProfileIncludesInactiveMembership() {
        User owner = persistUser(
                "rejoin-owner@example.com",
                "REJOINOWN01"
        );
        User memberUser = persistUser(
                "rejoin-member@example.com",
                "REJOINMEM01"
        );
        OpenChatRoom room = persistOpenRoom(
                owner,
                "재참여 테스트방",
                "설명",
                OpenChatVisibility.PUBLIC,
                "OC-OWNER"
        );
        ChatRoomMember member = entityManager.persist(
                ChatRoomMember.createMember(
                        room.getChatRoom(),
                        memberUser,
                        "ko",
                        "ja"
                )
        );
        entityManager.persist(OpenChatMemberProfile.create(
                member,
                "OC-KEEP1",
                "재참여고양이",
                null
        ));
        member.leaveOpenChat();
        entityManager.flush();
        entityManager.clear();

        OpenChatMemberProfileQueryRow profile =
                openChatRoomRepository.findMyProfile(
                        room.getChatRoom().getId(),
                        memberUser.getId()
                ).orElseThrow();

        assertThat(profile.active()).isFalse();
        assertThat(profile.memberCode()).isEqualTo("OC-KEEP1");
        assertThat(profile.nickname()).isEqualTo("재참여고양이");
    }

    private User persistUser(
            String email,
            String publicId
    ) {
        return entityManager.persist(User.createLocalUser(
                email,
                "password",
                email.substring(0, email.indexOf('@')),
                Role.USER,
                publicId
        ));
    }

    private OpenChatRoom persistOpenRoom(
            User owner,
            String name,
            String description,
            OpenChatVisibility visibility,
            String memberCode
    ) {
        ChatRoom room = entityManager.persist(
                ChatRoom.createOpenRoom(
                        name,
                        description,
                        owner
                )
        );
        OpenChatRoom openChatRoom = entityManager.persist(
                OpenChatRoom.create(
                        room,
                        visibility,
                        50
                )
        );
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
                memberCode,
                "방장고양이",
                null
        ));
        return openChatRoom;
    }
}
