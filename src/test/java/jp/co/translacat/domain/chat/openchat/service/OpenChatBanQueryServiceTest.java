package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.openchat.ban.repository.OpenChatBanQueryRow;
import jp.co.translacat.domain.chat.openchat.ban.repository.OpenChatBanRepository;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatBanListResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileImageUrlResolver;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenChatBanQueryServiceTest {

    @Mock private OpenChatBanRepository banRepository;
    @Mock private OpenChatAccessService accessService;
    @Mock private OpenChatProfileImageUrlResolver imageUrlResolver;

    private OpenChatBanQueryService service;
    private ChatRoomMember owner;
    private ChatRoomMember admin;

    @BeforeEach
    void setUp() {
        service = new OpenChatBanQueryService(
                banRepository,
                accessService,
                imageUrlResolver
        );

        User ownerUser = user(1L, "owner@open.test", "OPENOWNER1");
        User adminUser = user(2L, "admin@open.test", "OPENADMIN1");
        ChatRoom room = ChatRoom.createOpenRoom(
                "open",
                "desc",
                ownerUser
        );
        ReflectionTestUtils.setField(room, "id", 100L);

        owner = ChatRoomMember.createOwner(
                room,
                ownerUser,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(owner, "id", 10L);
        admin = ChatRoomMember.createMember(
                room,
                adminUser,
                "ko",
                "ja"
        );
        admin.changeRole(ChatRoomMemberRole.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 20L);
    }

    @Test
    void returnsSnapshotRowsAndOwnerCanReleaseAll() {
        when(accessService.getActiveOpenMember(1L, 100L))
                .thenReturn(owner);
        when(banRepository.findActivePage(
                100L,
                "cat",
                null,
                3
        )).thenReturn(List.of(
                row(72L, ChatRoomMemberRole.OWNER),
                row(71L, ChatRoomMemberRole.ADMIN),
                row(70L, ChatRoomMemberRole.ADMIN)
        ));
        when(imageUrlResolver.resolve("snapshot/avatar.png"))
                .thenReturn("https://cdn.test/snapshot.png");

        OpenChatBanListResponseDto response =
                service.getActiveBans(
                        1L,
                        100L,
                        " cat ",
                        null,
                        2
                );

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursorId()).isEqualTo(71L);
        assertThat(response.items())
                .allMatch(item -> item.releasable());
        assertThat(response.items().getFirst().memberCode())
                .isEqualTo("OC-SAME");
        assertThat(response.items().getFirst().profileImageUrl())
                .isEqualTo("https://cdn.test/snapshot.png");
    }

    @Test
    void adminCanReleaseOnlyAdminHierarchyMemberBan() {
        when(accessService.getActiveOpenMember(2L, 100L))
                .thenReturn(admin);
        when(banRepository.findActivePage(
                100L,
                null,
                null,
                21
        )).thenReturn(List.of(
                row(72L, ChatRoomMemberRole.OWNER),
                row(71L, ChatRoomMemberRole.ADMIN)
        ));

        OpenChatBanListResponseDto response =
                service.getActiveBans(
                        2L,
                        100L,
                        null,
                        null,
                        20
                );

        assertThat(response.items().get(0).releasable()).isFalse();
        assertThat(response.items().get(1).releasable()).isTrue();
    }

    private OpenChatBanQueryRow row(
            Long banId,
            ChatRoomMemberRole bannedByRole
    ) {
        return new OpenChatBanQueryRow(
                banId,
                100L,
                30L + banId,
                "OC-SAME",
                "같은고양이",
                "snapshot/avatar.png",
                LocalDateTime.of(2026, 7, 20, 10, 0),
                10L,
                "운영고양이",
                bannedByRole,
                LocalDateTime.of(2026, 8, 1, 12, 0),
                "reason",
                ChatRoomMemberRole.MEMBER
        );
    }

    private User user(Long id, String email, String publicId) {
        User user = User.createLocalUser(
                email,
                "password",
                email,
                Role.USER,
                publicId
        );
        user.setId(id);
        return user;
    }
}
