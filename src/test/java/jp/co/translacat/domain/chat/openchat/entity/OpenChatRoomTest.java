package jp.co.translacat.domain.chat.openchat.entity;

import jp.co.translacat.domain.chat.openchat.enums.OpenChatRoomStatus;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OpenChatRoomTest {

    @Test
    @DisplayName("OPEN 채팅방 확장 정보를 생성한다")
    void createOpenChatRoom() {
        User owner = createUser(1L);
        ChatRoom chatRoom = ChatRoom.createOpenRoom(
                "공개 대화방",
                "설명",
                owner
        );

        OpenChatRoom openChatRoom = OpenChatRoom.create(
                chatRoom,
                OpenChatVisibility.PUBLIC,
                50
        );

        assertThat(chatRoom.getRoomType())
                .isEqualTo(ChatRoomType.OPEN);
        assertThat(chatRoom.getSourceType())
                .isEqualTo(ChatRoomSourceType.OPEN);
        assertThat(openChatRoom.getVisibility())
                .isEqualTo(OpenChatVisibility.PUBLIC);
        assertThat(openChatRoom.getStatus())
                .isEqualTo(OpenChatRoomStatus.ACTIVE);
        assertThat(openChatRoom.getMaxMemberCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("OPEN이 아닌 채팅방에는 OPEN 확장 정보를 생성할 수 없다")
    void rejectNonOpenRoom() {
        User owner = createUser(1L);
        ChatRoom groupRoom = ChatRoom.createGroupRoom(
                "그룹",
                null,
                owner
        );

        assertThatIllegalArgumentException().isThrownBy(() ->
                OpenChatRoom.create(
                        groupRoom,
                        OpenChatVisibility.PUBLIC,
                        50
                )
        );
    }

    @Test
    @DisplayName("OPEN 채팅방을 종료한다")
    void closeRoom() {
        OpenChatRoom openChatRoom = OpenChatRoom.create(
                ChatRoom.createOpenRoom(
                        "공개 대화방",
                        "설명",
                        createUser(1L)
                ),
                OpenChatVisibility.UNLISTED,
                50
        );

        openChatRoom.close();

        assertThat(openChatRoom.isClosed()).isTrue();
        assertThat(openChatRoom.getClosedAt()).isNotNull();
    }

    private User createUser(Long id) {
        User user = User.createLocalUser(
                "owner@example.com",
                "password",
                "owner",
                Role.USER,
                "OPENOWNER01"
        );
        user.setId(id);
        return user;
    }
}
