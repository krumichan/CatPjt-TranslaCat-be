package jp.co.translacat.domain.chat.room.dto.response;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomSourceTypeResponseRegressionTest {

    @Test
    @DisplayName("채팅방 상세 응답은 sourceType을 유지한다")
    void responseKeepsSourceType() {
        // given
        User owner = createUser(1L, "owner@example.com", "owner", "TCAT-00000001");
        ChatRoom chatRoom = ChatRoom.createDirectRoom(
                owner,
                ChatRoomSourceType.FRIEND
        );
        ChatLanguageSettingResult languageSetting = new ChatLanguageSettingResult(
                "ko",
                "ja",
                true
        );

        // when
        ChatRoomResponseDto response = ChatRoomResponseDto.from(
                chatRoom,
                languageSetting,
                2L
        );

        // then
        assertThat(response.roomType()).isEqualTo(ChatRoomType.DIRECT);
        assertThat(response.sourceType()).isEqualTo(ChatRoomSourceType.FRIEND);
        assertThat(response.memberCount()).isEqualTo(2L);
        assertThat(response.originalLanguageCode()).isEqualTo("ko");
        assertThat(response.translationLanguageCode()).isEqualTo("ja");
        assertThat(response.roomLanguageSettingApplied()).isTrue();
    }

    @Test
    @DisplayName("채팅방 목록 응답 항목은 sourceType을 유지한다")
    void listItemKeepsSourceType() {
        // given
        User owner = createUser(1L, "owner@example.com", "owner", "TCAT-00000001");
        ChatRoom chatRoom = ChatRoom.createGroupRoom(
                "친구 그룹",
                "FRIEND 그룹",
                owner,
                ChatRoomSourceType.FRIEND
        );

        // when
        ChatRoomListItemResponseDto response = ChatRoomListItemResponseDto.from(
                chatRoom,
                3L
        );

        // then
        assertThat(response.roomType()).isEqualTo(ChatRoomType.GROUP);
        assertThat(response.sourceType()).isEqualTo(ChatRoomSourceType.FRIEND);
        assertThat(response.name()).isEqualTo("친구 그룹");
        assertThat(response.description()).isEqualTo("FRIEND 그룹");
        assertThat(response.memberCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("수동 생성 방 응답은 sourceType MANUAL을 유지한다")
    void manualRoomResponseKeepsManualSourceType() {
        // given
        User owner = createUser(1L, "owner@example.com", "owner", "TCAT-00000001");
        ChatRoom chatRoom = ChatRoom.createGroupRoom(
                "수동 그룹",
                "MANUAL 그룹",
                owner
        );

        // when
        ChatRoomListItemResponseDto response = ChatRoomListItemResponseDto.from(
                chatRoom,
                3L
        );

        // then
        assertThat(response.sourceType()).isEqualTo(ChatRoomSourceType.MANUAL);
    }

    private User createUser(
            Long id,
            String email,
            String username,
            String publicId
    ) {
        User user = User.createLocalUser(
                email,
                "password",
                username,
                Role.USER,
                publicId
        );
        user.setId(id);
        return user;
    }
}
