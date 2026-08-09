package jp.co.translacat.domain.chat.ai.entity;

import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageAiSenderTest {

    @Test
    void createsAiMessageWithoutPretendingAiIsUser() {
        User owner = User.createLocalUser(
                "owner@ai.test",
                "password",
                "owner",
                Role.USER,
                "AIOWNER01"
        );
        ChatRoom room = ChatRoom.createGroupRoom("group", "desc", owner);
        ChatAiAgent agent = ChatAiAgent.create(
                "Mika",
                null,
                "ja",
                "persona"
        );
        ChatRoomAiMember aiMember = ChatRoomAiMember.create(room, agent);

        ChatMessage message = ChatMessage.createAiTextMessage(
                room,
                aiMember,
                "こんにちは"
        );

        assertThat(message.getSenderType())
                .isEqualTo(ChatMessageSenderType.AI);
        assertThat(message.getSenderUser()).isNull();
        assertThat(message.getSenderAiMember()).isSameAs(aiMember);
        assertThat(message.isAiMessage()).isTrue();
    }
}
