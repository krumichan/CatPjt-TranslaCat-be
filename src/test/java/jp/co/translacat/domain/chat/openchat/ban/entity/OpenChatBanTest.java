package jp.co.translacat.domain.chat.openchat.ban.entity;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class OpenChatBanTest {

    @Test
    void keepsSnapshotAndReleaseHistoryWithoutPhysicalDelete() {
        User owner = user(1L, "owner@open.test", "OPENOWNER1");
        User target = user(2L, "target@open.test", "OPENTARGET1");
        ChatRoom room = ChatRoom.createOpenRoom("open", "desc", owner);
        ReflectionTestUtils.setField(room, "id", 100L);

        ChatRoomMember ownerMember = ChatRoomMember.createOwner(
                room, owner, "ko", "ja"
        );
        ChatRoomMember targetMember = ChatRoomMember.createMember(
                room, target, "ko", "ja"
        );
        ReflectionTestUtils.setField(ownerMember, "id", 10L);
        ReflectionTestUtils.setField(targetMember, "id", 20L);

        OpenChatBan ban = OpenChatBan.create(
                room,
                target,
                targetMember,
                "OC-ABCDE",
                "고양이",
                " snapshot/avatar.png ",
                targetMember.getJoinedAt(),
                ChatRoomMemberRole.MEMBER,
                ownerMember,
                ChatRoomMemberRole.OWNER,
                "reason"
        );

        assertThat(ban.isActive()).isTrue();
        assertThat(ban.getTargetMemberCode()).isEqualTo("OC-ABCDE");
        assertThat(ban.getProfileImageObjectKeySnapshot())
                .isEqualTo("snapshot/avatar.png");

        ban.release(ownerMember);

        assertThat(ban.isActive()).isFalse();
        assertThat(ban.getReleasedByMember()).isSameAs(ownerMember);
        assertThat(ban.getReleasedAt()).isNotNull();
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
