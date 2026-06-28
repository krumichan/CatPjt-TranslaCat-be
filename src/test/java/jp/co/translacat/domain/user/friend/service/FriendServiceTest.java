package jp.co.translacat.domain.user.friend.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.entity.Friend;
import jp.co.translacat.domain.user.friend.repository.FriendRepository;
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
class FriendServiceTest {

    @Mock
    private FriendRepository friendRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FriendService friendService;

    @Test
    @DisplayName("친구 관계 생성 테스트")
    void createFriend() {
        // given
        User user1 = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User user2 = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(friendRepository.findByUserIds(1L, 2L)).thenReturn(Optional.empty());
        when(friendRepository.save(any(Friend.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Friend friend = friendService.createFriend(1L, 2L);

        // then
        assertThat(friend.getUserLow()).isEqualTo(user1);
        assertThat(friend.getUserHigh()).isEqualTo(user2);
        assertThat(friend.isActive()).isTrue();

        verify(friendRepository).save(any(Friend.class));
    }

    @Test
    @DisplayName("중복 친구 관계 생성 방지 테스트")
    void failWhenFriendAlreadyExists() {
        // given
        User user1 = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User user2 = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");
        Friend existingFriend = Friend.create(user1, user2);

        when(friendRepository.findByUserIds(1L, 2L)).thenReturn(Optional.of(existingFriend));

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendService.createFriend(user1, user2))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_ALREADY_EXISTS")
                );

        verify(friendRepository, never()).save(any(Friend.class));
    }

    @Test
    @DisplayName("soft delete된 친구 관계는 재생성 시 복구된다")
    void restoreSoftDeletedFriend() {
        // given
        User user1 = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User user2 = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");
        Friend deletedFriend = Friend.create(user1, user2);
        deletedFriend.softDelete();

        when(friendRepository.findByUserIds(1L, 2L)).thenReturn(Optional.of(deletedFriend));

        // when
        Friend friend = friendService.createFriend(user1, user2);

        // then
        assertThat(friend.isActive()).isTrue();
        assertThat(friend.isDeleted()).isFalse();
        assertThat(friend.getDeletedAt()).isNull();

        verify(friendRepository, never()).save(any(Friend.class));
    }

    @Test
    @DisplayName("친구 여부 확인 테스트")
    void areFriends() {
        // given
        when(friendRepository.existsActiveByUserIds(1L, 2L)).thenReturn(true);

        // when & then
        assertThat(friendService.areFriends(1L, 2L)).isTrue();
        assertThat(friendService.areFriends(1L, 1L)).isFalse();
        assertThat(friendService.areFriends(null, 2L)).isFalse();
    }

    @Test
    @DisplayName("친구 삭제 테스트")
    void deleteFriend() {
        // given
        User user1 = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User user2 = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");
        Friend friend = Friend.create(user1, user2);

        when(friendRepository.findActiveByUserIds(1L, 2L)).thenReturn(Optional.of(friend));

        // when
        friendService.deleteFriend(1L, 2L);

        // then
        assertThat(friend.isDeleted()).isTrue();
        assertThat(friend.isActive()).isFalse();
        assertThat(friend.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 친구 관계 삭제 실패 테스트")
    void failWhenFriendDoesNotExist() {
        // given
        when(friendRepository.findActiveByUserIds(1L, 2L)).thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendService.deleteFriend(1L, 2L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_NOT_FOUND")
                );
    }

    @Test
    @DisplayName("사용자별 친구 관계 목록 조회 테스트")
    void getActiveFriendsByUserId() {
        // given
        User user1 = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User user2 = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");
        Friend friend = Friend.create(user1, user2);

        when(friendRepository.findActiveFriendsByUserId(1L)).thenReturn(List.of(friend));

        // when
        List<Friend> friends = friendService.getActiveFriendsByUserId(1L);

        // then
        assertThat(friends).hasSize(1);
        assertThat(friends.get(0).containsUser(1L)).isTrue();
        assertThat(friends.get(0).containsUser(2L)).isTrue();
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
