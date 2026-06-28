package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomFriendDirectCommandServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private UserService userService;

    @Mock
    private FriendChatValidationService friendChatValidationService;

    @InjectMocks
    private ChatRoomCommandService chatRoomCommandService;

    @Test
    @DisplayName("친구 1:1 방 신규 생성 시 공통 검증을 사용한다")
    void createFriendDirectRoom() {
        // given
        Long loginUserId = 1L;
        Long friendUserId = 2L;

        User loginUser = createUser(loginUserId, "login@example.com", "login", "TCAT-00000001");
        User friendUser = createUser(friendUserId, "friend@example.com", "friend", "TCAT-00000002");

        doNothing().when(friendChatValidationService).validateDirectTarget(loginUserId, friendUserId);
        when(userService.getById(loginUserId)).thenReturn(loginUser);
        when(userService.getById(friendUserId)).thenReturn(friendUser);
        when(chatRoomRepository.findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                friendUserId,
                ChatRoomSourceType.FRIEND
        )).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ChatRoom chatRoom = chatRoomCommandService.createOrGetFriendDirectRoom(
                loginUserId,
                friendUserId
        );

        // then
        assertThat(chatRoom.getRoomType()).isEqualTo(ChatRoomType.DIRECT);
        assertThat(chatRoom.getSourceType()).isEqualTo(ChatRoomSourceType.FRIEND);
        assertThat(chatRoom.getOwner()).isEqualTo(loginUser);

        verify(friendChatValidationService).validateDirectTarget(loginUserId, friendUserId);
        verify(chatRoomRepository).save(any(ChatRoom.class));
        verify(chatRoomMemberRepository, times(2)).save(any(ChatRoomMember.class));
    }

    @Test
    @DisplayName("기존 FRIEND DIRECT 방이 있으면 재사용한다")
    void reuseExistingFriendDirectRoom() {
        // given
        Long loginUserId = 1L;
        Long friendUserId = 2L;

        User loginUser = createUser(loginUserId, "login@example.com", "login", "TCAT-00000001");
        ChatRoom existingRoom = ChatRoom.createDirectRoom(
                loginUser,
                ChatRoomSourceType.FRIEND
        );

        doNothing().when(friendChatValidationService).validateDirectTarget(loginUserId, friendUserId);
        when(userService.getById(loginUserId)).thenReturn(loginUser);
        when(chatRoomRepository.findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                friendUserId,
                ChatRoomSourceType.FRIEND
        )).thenReturn(Optional.of(existingRoom));

        // when
        ChatRoom chatRoom = chatRoomCommandService.createOrGetFriendDirectRoom(
                loginUserId,
                friendUserId
        );

        // then
        assertThat(chatRoom).isEqualTo(existingRoom);
        assertThat(chatRoom.getSourceType()).isEqualTo(ChatRoomSourceType.FRIEND);

        verify(friendChatValidationService).validateDirectTarget(loginUserId, friendUserId);
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        verify(chatRoomMemberRepository, never()).save(any(ChatRoomMember.class));
    }

    @Test
    @DisplayName("공통 검증 실패 시 채팅방을 생성하지 않는다")
    void failWhenValidationFails() {
        // given
        Long loginUserId = 1L;
        Long friendUserId = 2L;

        doThrow(new BusinessException(
                "친구 관계인 사용자와만 친구 채팅을 시작할 수 있습니다.",
                "FRIEND_RELATION_REQUIRED"
        )).when(friendChatValidationService).validateDirectTarget(loginUserId, friendUserId);

        // when & then
        org.assertj.core.api.Assertions.assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> chatRoomCommandService.createOrGetFriendDirectRoom(
                        loginUserId,
                        friendUserId
                ))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_RELATION_REQUIRED")
                );

        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        verify(chatRoomMemberRepository, never()).save(any(ChatRoomMember.class));
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
