package jp.co.translacat.domain.chat.message.dto.response;

import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageResponseDtoAiTest {

    @Test
    void aiMessageExposesAiMemberIdentityWithoutUserIdentity() {
        User owner = User.createLocalUser(
                "owner@ai-response.test",
                "password",
                "owner",
                Role.USER,
                "AIRESPONOWN"
        );
        ChatRoom room = ChatRoom.createGroupRoom("group", null, owner);
        ReflectionTestUtils.setField(room, "id", 1L);
        ChatAiAgent agent = ChatAiAgent.create(
                "Mika",
                null,
                "ja",
                "persona"
        );
        ChatRoomAiMember aiMember = ChatRoomAiMember.create(room, agent);
        ReflectionTestUtils.setField(aiMember, "id", 10L);

        ChatMessage message = ChatMessage.createAiTextMessage(
                room,
                aiMember,
                "こんにちは",
                "chat-ai:mention:100:10"
        );
        ReflectionTestUtils.setField(message, "id", 101L);
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(message, "updatedAt", LocalDateTime.now());

        ChatMessageResponseDto response = ChatMessageResponseDto.fromAi(
                message,
                "https://cdn.example/ai.png",
                List.of(),
                2L
        );

        assertThat(response.senderType()).isEqualTo(ChatMessageSenderType.AI);
        assertThat(response.senderUserId()).isNull();
        assertThat(response.senderAiMemberId()).isEqualTo(10L);
        assertThat(response.senderName()).isEqualTo("Mika");
        assertThat(response.senderEmail()).isNull();
        assertThat(response.senderProfileImageUrl())
                .isEqualTo("https://cdn.example/ai.png");
        assertThat(response.unreadMemberCount()).isEqualTo(2L);
    }
}
