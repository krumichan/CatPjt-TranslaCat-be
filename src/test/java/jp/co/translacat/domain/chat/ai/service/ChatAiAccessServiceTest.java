package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAiAccessServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository memberRepository;

    private ChatAiAccessService service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new ChatAiAccessService(
                chatRoomRepository,
                memberRepository
        );
        owner = user(1L, "owner@ai.test", "AIOWNER01");
    }

    @Test
    void ownerCanManageGroupRoom() {
        ChatRoom room = ChatRoom.createGroupRoom("group", "desc", owner);
        ChatRoomMember ownerMember = ChatRoomMember.createOwner(
                room,
                owner,
                "ko",
                "ja"
        );

        when(chatRoomRepository.findByIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(Optional.of(room));
        when(memberRepository.findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                100L,
                1L
        )).thenReturn(Optional.of(ownerMember));

        assertThat(service.getManageableRoom(1L, 100L)).isSameAs(room);
    }

    @Test
    void normalMemberCannotManageAi() {
        ChatRoom room = ChatRoom.createGroupRoom("group", "desc", owner);
        User memberUser = user(2L, "member@ai.test", "AIMEMBER1");
        ChatRoomMember member = ChatRoomMember.createMember(
                room,
                memberUser,
                "ko",
                "ja"
        );

        when(chatRoomRepository.findByIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(Optional.of(room));
        when(memberRepository.findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                100L,
                2L
        )).thenReturn(Optional.of(member));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getManageableRoom(2L, 100L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ChatAiErrorCode.ROOM_MANAGEMENT_ACCESS_DENIED));
    }

    @Test
    void directRoomIsRejected() {
        ChatRoom room = ChatRoom.createDirectRoom(owner);
        when(chatRoomRepository.findByIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(Optional.of(room));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getAccessibleRoom(1L, 100L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ChatAiErrorCode.ROOM_TYPE_NOT_SUPPORTED));
    }

    private User user(Long id, String email, String publicId) {
        User user = User.createLocalUser(
                email,
                "password",
                email,
                Role.USER,
                publicId
        );
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
