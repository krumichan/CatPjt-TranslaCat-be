package jp.co.translacat.domain.chat.member.dto.response;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatRoomMemberResponseDtoTest {

    @Test
    void fromUsesLatestProfileSummaryWithoutEmail() {
        ChatRoomMember member = mock(ChatRoomMember.class);
        ChatRoom room = mock(ChatRoom.class);
        User user = mock(User.class);

        LocalDateTime joinedAt =
                LocalDateTime.of(
                        2026,
                        7,
                        20,
                        12,
                        0
                );

        when(member.getId()).thenReturn(10L);
        when(member.getChatRoom()).thenReturn(room);
        when(member.getUser()).thenReturn(user);
        when(member.getRole())
                .thenReturn(ChatRoomMemberRole.MEMBER);
        when(member.isActive()).thenReturn(true);
        when(member.getJoinedAt()).thenReturn(joinedAt);
        when(room.getId()).thenReturn(100L);
        when(user.getId()).thenReturn(2L);

        UserSummaryProfileResponseDto profile =
                new UserSummaryProfileResponseDto(
                        2L,
                        "TCAT-00000002",
                        "그룹 멤버",
                        "https://cdn.example.com/profile.png",
                        null,
                        null
                );

        ChatRoomMemberResponseDto response =
                ChatRoomMemberResponseDto.from(
                        member,
                        profile
                );

        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.publicId())
                .isEqualTo("TCAT-00000002");
        assertThat(response.displayName())
                .isEqualTo("그룹 멤버");
        assertThat(response.profileImageUrl())
                .isEqualTo(
                        "https://cdn.example.com/profile.png"
                );
        assertThat(response.role())
                .isEqualTo(
                        ChatRoomMemberRole.MEMBER
                );
        assertThat(response.joinedAt())
                .isEqualTo(joinedAt);
    }
}
