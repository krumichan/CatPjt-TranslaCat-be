package jp.co.translacat.domain.chat.ai.entity;

import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.enums.ChatAiMentionPermission;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomAiSettingTest {

    @Test
    void startsWithPublicAllMembersAndAutonomousFeaturesEnabled() {
        ChatRoom room = ChatRoom.createGroupRoom(
                "group",
                "desc",
                user()
        );

        ChatRoomAiSetting setting = ChatRoomAiSetting.createDefault(room);

        assertThat(setting.getDisclosureType())
                .isEqualTo(ChatAiDisclosureType.PUBLIC);
        assertThat(setting.getMentionPermission())
                .isEqualTo(ChatAiMentionPermission.ALL_MEMBERS);
        assertThat(setting.isConversationEnabled()).isTrue();
        assertThat(setting.isRevivalEnabled()).isTrue();
    }

    @Test
    void partialUpdateChangesOnlyRequestedValues() {
        ChatRoomAiSetting setting = ChatRoomAiSetting.createDefault(
                ChatRoom.createGroupRoom("group", "desc", user())
        );

        setting.update(
                ChatAiDisclosureType.PRIVATE,
                null,
                false,
                null
        );

        assertThat(setting.getDisclosureType())
                .isEqualTo(ChatAiDisclosureType.PRIVATE);
        assertThat(setting.getMentionPermission())
                .isEqualTo(ChatAiMentionPermission.ALL_MEMBERS);
        assertThat(setting.isConversationEnabled()).isFalse();
        assertThat(setting.isRevivalEnabled()).isTrue();
    }

    private User user() {
        return User.createLocalUser(
                "owner@ai.test",
                "password",
                "owner",
                Role.USER,
                "AIOWNER001"
        );
    }
}
