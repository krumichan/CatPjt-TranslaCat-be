package jp.co.translacat.domain.chat.openchat.profile.entity;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OpenChatMemberProfileTest {

    @Test
    @DisplayName("OPEN 채팅방 멤버의 방별 프로필을 생성한다")
    void createProfile() {
        User user = createUser(1L);
        ChatRoom room = ChatRoom.createOpenRoom(
                "공개방",
                "설명",
                user
        );
        OpenChatRoom.create(room, OpenChatVisibility.PUBLIC, 50);
        ChatRoomMember member = ChatRoomMember.createOwner(
                room,
                user,
                "ko",
                "ja"
        );

        OpenChatMemberProfile profile =
                OpenChatMemberProfile.create(
                        member,
                        "OC-A7K2M",
                        "고양이",
                        "open-chat-profiles/481/avatar.png"
                );

        assertThat(profile.getMemberCode()).isEqualTo("OC-A7K2M");
        assertThat(profile.getNickname()).isEqualTo("고양이");
        assertThat(profile.getProfileImageObjectKey())
                .isEqualTo(
                        "open-chat-profiles/481/avatar.png"
                );
    }

    @Test
    @DisplayName("일반 GROUP 멤버의 OPEN 프로필 생성은 거부한다")
    void rejectGroupMember() {
        User user = createUser(1L);
        ChatRoom room = ChatRoom.createGroupRoom(
                "그룹",
                null,
                user
        );
        ChatRoomMember member = ChatRoomMember.createOwner(
                room,
                user,
                "ko",
                "ja"
        );

        assertThatIllegalArgumentException().isThrownBy(() ->
                OpenChatMemberProfile.create(
                        member,
                        "OC-A7K2M",
                        "고양이",
                        null
                )
        );
    }

    private User createUser(Long id) {
        User user = User.createLocalUser(
                "profile@example.com",
                "password",
                "profile",
                Role.USER,
                "OPENPROF001"
        );
        user.setId(id);
        return user;
    }
}
