package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.dto.request.ChatRoomCreateRequestDto;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomManualCreationRegressionTest {

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
    @DisplayName("기존 수동 1:1 채팅방 생성은 sourceType MANUAL을 유지한다")
    void createManualDirectRoomWithManualSourceType() {
        // given
        Long loginUserId = 1L;
        Long targetUserId = 2L;

        User owner = createUser(loginUserId, "owner@example.com", "owner", "TCAT-00000001");
        User targetUser = createUser(targetUserId, "target@example.com", "target", "TCAT-00000002");

        ChatRoomCreateRequestDto request = new ChatRoomCreateRequestDto(
                ChatRoomType.DIRECT,
                null,
                null,
                List.of(targetUserId)
        );

        when(userService.getById(loginUserId)).thenReturn(owner);
        when(userService.getById(targetUserId)).thenReturn(targetUser);
        when(chatRoomRepository.findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                targetUserId,
                ChatRoomSourceType.MANUAL
        )).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ChatRoom chatRoom = chatRoomCommandService.create(
                loginUserId,
                request
        );

        // then
        assertThat(chatRoom.getRoomType()).isEqualTo(ChatRoomType.DIRECT);
        assertThat(chatRoom.getSourceType()).isEqualTo(ChatRoomSourceType.MANUAL);
        assertThat(chatRoom.getOwner()).isEqualTo(owner);

        verify(chatRoomRepository).findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                targetUserId,
                ChatRoomSourceType.MANUAL
        );
        verify(chatRoomRepository, never()).findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                targetUserId,
                ChatRoomSourceType.FRIEND
        );
        verify(chatRoomMemberRepository, times(2)).save(any(ChatRoomMember.class));
    }

    @Test
    @DisplayName("기존 수동 1:1 채팅방은 MANUAL 방만 재사용한다")
    void reuseOnlyManualDirectRoom() {
        // given
        Long loginUserId = 1L;
        Long targetUserId = 2L;

        User owner = createUser(loginUserId, "owner@example.com", "owner", "TCAT-00000001");
        ChatRoom existingManualRoom = ChatRoom.createDirectRoom(
                owner,
                ChatRoomSourceType.MANUAL
        );

        ChatRoomCreateRequestDto request = new ChatRoomCreateRequestDto(
                ChatRoomType.DIRECT,
                null,
                null,
                List.of(targetUserId)
        );

        when(userService.getById(loginUserId)).thenReturn(owner);
        when(chatRoomRepository.findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                targetUserId,
                ChatRoomSourceType.MANUAL
        )).thenReturn(Optional.of(existingManualRoom));

        // when
        ChatRoom chatRoom = chatRoomCommandService.create(
                loginUserId,
                request
        );

        // then
        assertThat(chatRoom).isEqualTo(existingManualRoom);
        assertThat(chatRoom.getSourceType()).isEqualTo(ChatRoomSourceType.MANUAL);

        verify(chatRoomRepository).findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                targetUserId,
                ChatRoomSourceType.MANUAL
        );
        verify(chatRoomRepository, never()).findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                targetUserId,
                ChatRoomSourceType.FRIEND
        );
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        verify(chatRoomMemberRepository, never()).save(any(ChatRoomMember.class));
    }

    @Test
    @DisplayName("FRIEND DIRECT 방이 있어도 수동 1:1 생성은 MANUAL 기준으로 분리된다")
    void manualDirectRoomDoesNotReuseFriendDirectRoom() {
        // given
        Long loginUserId = 1L;
        Long targetUserId = 2L;

        User owner = createUser(loginUserId, "owner@example.com", "owner", "TCAT-00000001");
        User targetUser = createUser(targetUserId, "target@example.com", "target", "TCAT-00000002");

        ChatRoomCreateRequestDto request = new ChatRoomCreateRequestDto(
                ChatRoomType.DIRECT,
                null,
                null,
                List.of(targetUserId)
        );

        when(userService.getById(loginUserId)).thenReturn(owner);
        when(userService.getById(targetUserId)).thenReturn(targetUser);
        when(chatRoomRepository.findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                targetUserId,
                ChatRoomSourceType.MANUAL
        )).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ChatRoom chatRoom = chatRoomCommandService.create(
                loginUserId,
                request
        );

        // then
        assertThat(chatRoom.getSourceType()).isEqualTo(ChatRoomSourceType.MANUAL);

        verify(chatRoomRepository).findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                targetUserId,
                ChatRoomSourceType.MANUAL
        );
        verify(chatRoomRepository, never()).findActiveDirectRoomByUserIdsAndSourceType(
                loginUserId,
                targetUserId,
                ChatRoomSourceType.FRIEND
        );
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    @DisplayName("기존 수동 그룹 채팅방 생성은 sourceType MANUAL을 유지한다")
    void createManualGroupRoomWithManualSourceType() {
        // given
        Long loginUserId = 1L;
        User owner = createUser(1L, "owner@example.com", "owner", "TCAT-00000001");
        User member1 = createUser(2L, "member1@example.com", "member1", "TCAT-00000002");
        User member2 = createUser(3L, "member2@example.com", "member2", "TCAT-00000003");

        ChatRoomCreateRequestDto request = new ChatRoomCreateRequestDto(
                ChatRoomType.GROUP,
                "수동 그룹 채팅",
                "기존 수동 생성 API",
                List.of(2L, 3L)
        );

        when(userService.getById(1L)).thenReturn(owner);
        when(userService.getById(2L)).thenReturn(member1);
        when(userService.getById(3L)).thenReturn(member2);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ChatRoom chatRoom = chatRoomCommandService.create(
                loginUserId,
                request
        );

        // then
        assertThat(chatRoom.getRoomType()).isEqualTo(ChatRoomType.GROUP);
        assertThat(chatRoom.getSourceType()).isEqualTo(ChatRoomSourceType.MANUAL);
        assertThat(chatRoom.getName()).isEqualTo("수동 그룹 채팅");
        assertThat(chatRoom.getDescription()).isEqualTo("기존 수동 생성 API");

        verify(chatRoomMemberRepository, times(3)).save(any(ChatRoomMember.class));
        verifyNoInteractions(friendChatValidationService);
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
