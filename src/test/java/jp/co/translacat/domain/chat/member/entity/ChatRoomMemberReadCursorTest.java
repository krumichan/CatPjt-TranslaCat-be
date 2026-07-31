package jp.co.translacat.domain.chat.member.entity;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatRoomMemberReadCursorTest {

    @Test
    void readCursorAdvancesOnlyForward() {
        ChatRoomMember member = ChatRoomMember.createMember(
                mock(ChatRoom.class),
                mock(User.class),
                "ko",
                "ja"
        );

        assertThat(member.advanceReadCursor(10L)).isTrue();
        assertThat(member.getLastReadMessageId()).isEqualTo(10L);
        assertThat(member.getLastReadAt()).isNotNull();

        assertThat(member.advanceReadCursor(9L)).isFalse();
        assertThat(member.advanceReadCursor(10L)).isFalse();
        assertThat(member.getLastReadMessageId()).isEqualTo(10L);

        assertThat(member.advanceReadCursor(11L)).isTrue();
        assertThat(member.getLastReadMessageId()).isEqualTo(11L);
    }

    @Test
    void initializeReadCursorUsesInvitationBaseline() {
        ChatRoomMember member = ChatRoomMember.createMember(
                mock(ChatRoom.class),
                mock(User.class),
                "ko",
                "ja"
        );

        member.initializeReadCursor(25L);

        assertThat(member.getLastReadMessageId()).isEqualTo(25L);
        assertThat(member.getLastReadAt()).isNotNull();
    }

    @Test
    void restoreResetsPreviousParticipationReadCursor() {
        ChatRoomMember member = ChatRoomMember.createMember(
                mock(ChatRoom.class),
                mock(User.class),
                "ko",
                "ja"
        );
        member.initializeReadCursor(30L);

        member.restore(
                ChatRoomMemberRole.MEMBER,
                "ja",
                "ko",
                true,
                true
        );

        assertThat(member.getLastReadMessageId()).isNull();
        assertThat(member.getLastReadAt()).isNull();
    }
}
