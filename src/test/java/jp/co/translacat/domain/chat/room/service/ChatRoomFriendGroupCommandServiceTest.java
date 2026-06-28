package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.dto.request.FriendGroupChatRoomCreateRequestDto;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomFriendGroupCommandServiceTest {

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
    @DisplayName("친구 그룹 채팅방 생성 테스트")
    void createFriendGroupRoom() {
        // given
        Long loginUserId = 1L;
        User owner = createUser(1L, "owner@example.com", "owner", "TCAT-00000001");
        User member1 = createUser(2L, "member1@example.com", "member1", "TCAT-00000002");
        User member2 = createUser(3L, "member2@example.com", "member2", "TCAT-00000003");

        FriendGroupChatRoomCreateRequestDto request = new FriendGroupChatRoomCreateRequestDto(
                "친구 그룹 채팅",
                "친구들과 이야기하는 채팅방",
                List.of(2L, 3L)
        );

        doNothing().when(friendChatValidationService).validateGroupMembers(
                loginUserId,
                request.memberUserIds()
        );
        when(userService.getById(1L)).thenReturn(owner);
        when(userService.getById(2L)).thenReturn(member1);
        when(userService.getById(3L)).thenReturn(member2);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ChatRoom chatRoom = chatRoomCommandService.createFriendGroupRoom(
                loginUserId,
                request
        );

        // then
        assertThat(chatRoom.getRoomType()).isEqualTo(ChatRoomType.GROUP);
        assertThat(chatRoom.getSourceType()).isEqualTo(ChatRoomSourceType.FRIEND);
        assertThat(chatRoom.getName()).isEqualTo("친구 그룹 채팅");
        assertThat(chatRoom.getDescription()).isEqualTo("친구들과 이야기하는 채팅방");
        assertThat(chatRoom.getOwner()).isEqualTo(owner);

        ArgumentCaptor<ChatRoomMember> memberCaptor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberRepository, times(3)).save(memberCaptor.capture());

        List<ChatRoomMember> savedMembers = memberCaptor.getAllValues();

        assertThat(savedMembers).hasSize(3);
        assertThat(savedMembers.get(0).isOwner()).isTrue();
        assertThat(savedMembers.get(0).getUser()).isEqualTo(owner);
        assertThat(savedMembers.get(1).isMember()).isTrue();
        assertThat(savedMembers.get(1).getUser()).isEqualTo(member1);
        assertThat(savedMembers.get(2).isMember()).isTrue();
        assertThat(savedMembers.get(2).getUser()).isEqualTo(member2);

        verify(friendChatValidationService).validateGroupMembers(loginUserId, request.memberUserIds());
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    @DisplayName("친구 그룹 채팅방 생성 시 중복 멤버는 한 번만 추가한다")
    void createFriendGroupRoomWithDuplicatedMembers() {
        // given
        Long loginUserId = 1L;
        User owner = createUser(1L, "owner@example.com", "owner", "TCAT-00000001");
        User member1 = createUser(2L, "member1@example.com", "member1", "TCAT-00000002");
        User member2 = createUser(3L, "member2@example.com", "member2", "TCAT-00000003");

        FriendGroupChatRoomCreateRequestDto request = new FriendGroupChatRoomCreateRequestDto(
                "친구 그룹 채팅",
                null,
                List.of(2L, 3L, 2L)
        );

        doNothing().when(friendChatValidationService).validateGroupMembers(
                loginUserId,
                request.memberUserIds()
        );
        when(userService.getById(1L)).thenReturn(owner);
        when(userService.getById(2L)).thenReturn(member1);
        when(userService.getById(3L)).thenReturn(member2);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ChatRoom chatRoom = chatRoomCommandService.createFriendGroupRoom(
                loginUserId,
                request
        );

        // then
        assertThat(chatRoom.getSourceType()).isEqualTo(ChatRoomSourceType.FRIEND);

        verify(chatRoomMemberRepository, times(3)).save(any(ChatRoomMember.class));
        verify(userService, times(1)).getById(2L);
        verify(userService, times(1)).getById(3L);
    }

    @Test
    @DisplayName("친구 그룹 채팅 멤버가 비어 있으면 실패한다")
    void failWhenMemberUserIdsIsEmpty() {
        // given
        FriendGroupChatRoomCreateRequestDto request = new FriendGroupChatRoomCreateRequestDto(
                "친구 그룹 채팅",
                null,
                List.of()
        );

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> chatRoomCommandService.createFriendGroupRoom(1L, request))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_GROUP_MEMBER_REQUIRED")
                );

        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    @DisplayName("공통 검증 실패 시 친구 그룹 채팅방을 생성하지 않는다")
    void failWhenValidationFails() {
        // given
        Long loginUserId = 1L;
        FriendGroupChatRoomCreateRequestDto request = new FriendGroupChatRoomCreateRequestDto(
                "친구 그룹 채팅",
                null,
                List.of(2L, 3L)
        );

        doThrow(new BusinessException(
                "친구 관계인 사용자와만 친구 채팅을 시작할 수 있습니다.",
                "FRIEND_RELATION_REQUIRED"
        )).when(friendChatValidationService).validateGroupMembers(
                loginUserId,
                request.memberUserIds()
        );

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> chatRoomCommandService.createFriendGroupRoom(loginUserId, request))
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
