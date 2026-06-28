package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.service.FriendService;
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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
    private FriendService friendService;

    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private ChatRoomCommandService chatRoomCommandService;

    @Test
    @DisplayName("친구 1:1 방 신규 생성")
    void createFriendDirectRoom() {
        // given
        Long loginUserId = 1L;
        Long friendUserId = 2L;

        User loginUser = createUser(loginUserId, "login@example.com", "login", "TCAT-00000001");
        User friendUser = createUser(friendUserId, "friend@example.com", "friend", "TCAT-00000002");

        when(friendService.areFriends(loginUserId, friendUserId)).thenReturn(true);
        when(userBlockService.isBlockedBetween(loginUserId, friendUserId)).thenReturn(false);
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

        when(friendService.areFriends(loginUserId, friendUserId)).thenReturn(true);
        when(userBlockService.isBlockedBetween(loginUserId, friendUserId)).thenReturn(false);
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

        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        verify(chatRoomMemberRepository, never()).save(any(ChatRoomMember.class));
    }

    @Test
    @DisplayName("기존 MANUAL DIRECT 방은 재사용하지 않고 FRIEND DIRECT 방을 새로 생성한다")
    void doNotReuseManualDirectRoom() {
        // given
        Long loginUserId = 1L;
        Long friendUserId = 2L;

        User loginUser = createUser(loginUserId, "login@example.com", "login", "TCAT-00000001");
        User friendUser = createUser(friendUserId, "friend@example.com", "friend", "TCAT-00000002");

        when(friendService.areFriends(loginUserId, friendUserId)).thenReturn(true);
        when(userBlockService.isBlockedBetween(loginUserId, friendUserId)).thenReturn(false);
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
        assertThat(chatRoom.getSourceType()).isEqualTo(ChatRoomSourceType.FRIEND);

        verify(chatRoomRepository).findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                friendUserId,
                ChatRoomSourceType.FRIEND
        );
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    @DisplayName("친구가 아닌 사용자와는 1:1 친구 채팅을 시작할 수 없다")
    void failWhenNotFriend() {
        // given
        Long loginUserId = 1L;
        Long friendUserId = 2L;

        when(friendService.areFriends(loginUserId, friendUserId)).thenReturn(false);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> chatRoomCommandService.createOrGetFriendDirectRoom(
                        loginUserId,
                        friendUserId
                ))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_RELATION_REQUIRED")
                );

        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    @DisplayName("차단 관계가 있으면 1:1 친구 채팅을 시작할 수 없다")
    void failWhenBlockedBetweenUsers() {
        // given
        Long loginUserId = 1L;
        Long friendUserId = 2L;

        when(friendService.areFriends(loginUserId, friendUserId)).thenReturn(true);
        when(userBlockService.isBlockedBetween(loginUserId, friendUserId)).thenReturn(true);

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> chatRoomCommandService.createOrGetFriendDirectRoom(
                        loginUserId,
                        friendUserId
                ))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_BLOCKED_BETWEEN")
                );

        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
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
