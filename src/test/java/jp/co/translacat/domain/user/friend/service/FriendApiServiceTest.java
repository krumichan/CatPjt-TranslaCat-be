package jp.co.translacat.domain.user.friend.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.dto.FriendResponseDto;
import jp.co.translacat.domain.user.friend.entity.Friend;
import jp.co.translacat.domain.user.friend.repository.FriendRepository;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileQueryService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendApiServiceTest {

    @Mock
    private FriendRepository friendRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    @InjectMocks
    private FriendService friendService;

    @Test
    @DisplayName("친구 목록 조회 테스트")
    void getFriends() {
        // given
        User loginUser = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User friendUser = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");
        Friend friend = Friend.create(loginUser, friendUser);

        when(friendRepository.findActiveFriendsByUserId(1L)).thenReturn(List.of(friend));
        when(userProfileQueryService.getSummaryByUser(friendUser)).thenReturn(new UserSummaryProfileResponseDto(
                friendUser.getId(),
                friendUser.getPublicId(),
                friendUser.getUsername(),
                null
        ));

        // when
        List<FriendResponseDto> responses = friendService.getFriends(1L);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).friend().userId()).isEqualTo(2L);
        assertThat(responses.get(0).friend().publicId()).isEqualTo("TCAT-00000002");
        assertThat(responses.get(0).friend().nickname()).isEqualTo("user2");
    }

    @Test
    @DisplayName("친구 목록 조회 시 user_low/user_high 순서와 관계없이 상대 사용자를 반환한다")
    void getFriendsWhenLoginUserIsUserHigh() {
        // given
        User friendUser = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User loginUser = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");
        Friend friend = Friend.create(loginUser, friendUser);

        when(friendRepository.findActiveFriendsByUserId(2L)).thenReturn(List.of(friend));
        when(userProfileQueryService.getSummaryByUser(friendUser)).thenReturn(new UserSummaryProfileResponseDto(
                friendUser.getId(),
                friendUser.getPublicId(),
                friendUser.getUsername(),
                null
        ));

        // when
        List<FriendResponseDto> responses = friendService.getFriends(2L);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).friend().userId()).isEqualTo(1L);
        assertThat(responses.get(0).friend().publicId()).isEqualTo("TCAT-00000001");
    }

    @Test
    @DisplayName("친구 삭제 테스트")
    void deleteFriend() {
        // given
        User loginUser = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User friendUser = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");
        Friend friend = Friend.create(loginUser, friendUser);

        when(friendRepository.findActiveByUserIds(1L, 2L)).thenReturn(Optional.of(friend));

        // when
        friendService.deleteFriend(1L, 2L);

        // then
        assertThat(friend.isDeleted()).isTrue();
        assertThat(friend.isActive()).isFalse();
        assertThat(friend.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("친구 관계가 없으면 삭제할 수 없다")
    void failWhenDeleteNonFriend() {
        // given
        when(friendRepository.findActiveByUserIds(1L, 2L)).thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> friendService.deleteFriend(1L, 2L))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_NOT_FOUND")
                );
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
