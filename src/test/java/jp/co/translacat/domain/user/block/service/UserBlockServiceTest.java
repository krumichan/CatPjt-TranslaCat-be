package jp.co.translacat.domain.user.block.service;

import jp.co.translacat.domain.user.block.entity.UserBlock;
import jp.co.translacat.domain.user.block.repository.UserBlockRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @Mock
    private UserBlockRepository userBlockRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendService friendService;

    @InjectMocks
    private UserBlockService userBlockService;

    @Test
    @DisplayName("사용자 차단 생성 테스트")
    void blockUser() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");

        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.findByBlockerAndBlocked(1L, 2L)).thenReturn(Optional.empty());
        when(userBlockRepository.save(any(UserBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(friendService.areFriends(1L, 2L)).thenReturn(false);

        // when
        UserBlock userBlock = userBlockService.blockUser(1L, 2L);

        // then
        assertThat(userBlock.getBlockerUser()).isEqualTo(blocker);
        assertThat(userBlock.getBlockedUser()).isEqualTo(blocked);
        assertThat(userBlock.isActive()).isTrue();

        verify(userBlockRepository).save(any(UserBlock.class));
        verify(friendService, never()).deleteFriend(anyLong(), anyLong());
    }

    @Test
    @DisplayName("친구인 사용자를 차단하면 친구 관계를 soft delete 한다")
    void blockFriendUserDeletesFriendRelation() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");

        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.findByBlockerAndBlocked(1L, 2L)).thenReturn(Optional.empty());
        when(userBlockRepository.save(any(UserBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(friendService.areFriends(1L, 2L)).thenReturn(true);

        // when
        UserBlock userBlock = userBlockService.blockUser(1L, 2L);

        // then
        assertThat(userBlock.isActive()).isTrue();

        verify(friendService).deleteFriend(1L, 2L);
    }

    @Test
    @DisplayName("이미 차단한 사용자는 중복 차단할 수 없다")
    void failWhenUserBlockAlreadyExists() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");
        UserBlock existingBlock = UserBlock.create(blocker, blocked);

        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.findByBlockerAndBlocked(1L, 2L)).thenReturn(Optional.of(existingBlock));

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userBlockService.blockUser(1L, 2L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_BLOCK_ALREADY_EXISTS")
                );

        verify(userBlockRepository, never()).save(any(UserBlock.class));
    }

    @Test
    @DisplayName("soft delete된 차단 관계는 재차단 시 복구된다")
    void restoreSoftDeletedBlock() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");
        UserBlock deletedBlock = UserBlock.create(blocker, blocked);
        deletedBlock.softDelete();

        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.findByBlockerAndBlocked(1L, 2L)).thenReturn(Optional.of(deletedBlock));
        when(friendService.areFriends(1L, 2L)).thenReturn(false);

        // when
        UserBlock userBlock = userBlockService.blockUser(1L, 2L);

        // then
        assertThat(userBlock.isActive()).isTrue();
        assertThat(userBlock.isDeleted()).isFalse();
        assertThat(userBlock.getDeletedAt()).isNull();

        verify(userBlockRepository, never()).save(any(UserBlock.class));
    }

    @Test
    @DisplayName("차단 여부 확인 테스트")
    void isBlockedBetween() {
        // given
        when(userBlockRepository.existsActiveBlockBetweenUsers(1L, 2L)).thenReturn(true);

        // when & then
        assertThat(userBlockService.isBlockedBetween(1L, 2L)).isTrue();
        assertThat(userBlockService.isBlockedBetween(2L, 1L)).isFalse();
        assertThat(userBlockService.isBlockedBetween(1L, 1L)).isFalse();
        assertThat(userBlockService.isBlockedBetween(null, 2L)).isFalse();
    }

    @Test
    @DisplayName("차단 방향 확인 테스트")
    void isBlocking() {
        // given
        when(userBlockRepository.existsActiveByBlockerAndBlocked(1L, 2L)).thenReturn(true);

        // when & then
        assertThat(userBlockService.isBlocking(1L, 2L)).isTrue();
        assertThat(userBlockService.isBlocking(2L, 1L)).isFalse();
        assertThat(userBlockService.isBlocking(1L, 1L)).isFalse();
    }

    @Test
    @DisplayName("차단 해제 테스트")
    void unblockUser() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");
        UserBlock userBlock = UserBlock.create(blocker, blocked);

        when(userBlockRepository.findActiveByBlockerAndBlocked(1L, 2L)).thenReturn(Optional.of(userBlock));

        // when
        userBlockService.unblockUser(1L, 2L);

        // then
        assertThat(userBlock.isDeleted()).isTrue();
        assertThat(userBlock.isActive()).isFalse();
        assertThat(userBlock.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("차단 관계가 없으면 차단 해제할 수 없다")
    void failWhenUnblockNonBlockedUser() {
        // given
        when(userBlockRepository.findActiveByBlockerAndBlocked(1L, 2L)).thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userBlockService.unblockUser(1L, 2L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_BLOCK_NOT_FOUND")
                );
    }

    @Test
    @DisplayName("내가 차단한 사용자 목록 조회 테스트")
    void getActiveBlocksByBlockerUserId() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");
        UserBlock userBlock = UserBlock.create(blocker, blocked);

        when(userBlockRepository.findActiveBlocksByBlockerUserId(1L)).thenReturn(List.of(userBlock));

        // when
        List<UserBlock> userBlocks = userBlockService.getActiveBlocksByBlockerUserId(1L);

        // then
        assertThat(userBlocks).hasSize(1);
        assertThat(userBlocks.get(0).getBlockerUser().getId()).isEqualTo(1L);
        assertThat(userBlocks.get(0).getBlockedUser().getId()).isEqualTo(2L);
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
