package jp.co.translacat.domain.chat.message.dto.response;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMessageSenderResponseDto;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageResponseDtoOpenChatTest {

    @Test
    void hidesOrdinaryIdentityAndUsesRoomScopedSender() {
        User senderUser = User.createLocalUser(
                "ordinary-email@open.test",
                "password",
                "ordinary-username",
                Role.USER,
                "ORDINARY01"
        );
        senderUser.setId(2L);
        ChatRoom room = ChatRoom.createOpenRoom(
                "open",
                "desc",
                senderUser
        );
        ReflectionTestUtils.setField(room, "id", 100L);
        ChatMessage message = ChatMessage.createUserTextMessage(
                room,
                senderUser,
                "hello"
        );
        ReflectionTestUtils.setField(message, "id", 200L);
        ReflectionTestUtils.setField(
                message,
                "createdAt",
                LocalDateTime.of(2026, 8, 1, 12, 0)
        );
        ReflectionTestUtils.setField(
                message,
                "updatedAt",
                LocalDateTime.of(2026, 8, 1, 12, 0)
        );

        OpenChatMessageSenderResponseDto openSender =
                new OpenChatMessageSenderResponseDto(
                        20L,
                        "OC-ABCDE",
                        "room-nickname",
                        "https://cdn.test/cat.png",
                        ChatRoomMemberRole.MEMBER
                );

        ChatMessageResponseDto result =
                ChatMessageResponseDto.fromOpenChat(
                        message,
                        openSender,
                        List.of(),
                        3L
                );

        assertThat(result.senderUserId()).isNull();
        assertThat(result.senderName()).isNull();
        assertThat(result.senderEmail()).isNull();
        assertThat(result.senderProfileImageUrl()).isNull();
        assertThat(result.sender()).isEqualTo(openSender);
        assertThat(result.unreadMemberCount()).isEqualTo(3L);
    }
}
