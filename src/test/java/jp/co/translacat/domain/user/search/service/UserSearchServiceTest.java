package jp.co.translacat.domain.user.search.service;

import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileQueryService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.domain.user.search.dto.UserSearchResponseDto;
import jp.co.translacat.domain.user.search.enums.UserSearchFriendStatus;
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
class UserSearchServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    @Mock
    private FriendService friendService;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private UserSearchService userSearchService;

    @Test
    @DisplayName("publicId 검색 성공 - 관계 없음")
    void searchByPublicIdWithNoneStatus() {
        // given
        Long loginUserId = 1L;
        User targetUser = createUser(2L, "target@example.com", "targetUser", "TCAT-00000002");
        UserSummaryProfileResponseDto profile = createSummary(targetUser);

        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(targetUser));
        when(userProfileQueryService.getSummaryByUser(targetUser)).thenReturn(profile);
        when(userBlockService.isBlockedBetween(loginUserId, 2L)).thenReturn(false);
        when(friendService.areFriends(loginUserId, 2L)).thenReturn(false);
        when(friendRequestRepository.findBetweenUsersByStatus(
                loginUserId,
                2L,
                FriendRequestStatus.PENDING
        )).thenReturn(Optional.empty());

        // when
        UserSearchResponseDto response = userSearchService.searchByPublicId(
                loginUserId,
                "TCAT-00000002"
        );

        // then
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.publicId()).isEqualTo("TCAT-00000002");
        assertThat(response.nickname()).isEqualTo("targetUser");
        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.friendStatus()).isEqualTo(UserSearchFriendStatus.NONE);
    }

    @Test
    @DisplayName("자기 자신 검색 처리")
    void searchSelf() {
        // given
        Long loginUserId = 1L;
        User loginUser = createUser(1L, "me@example.com", "me", "TCAT-00000001");

        when(userRepository.findByPublicId("TCAT-00000001")).thenReturn(Optional.of(loginUser));
        when(userProfileQueryService.getSummaryByUser(loginUser)).thenReturn(createSummary(loginUser));

        // when
        UserSearchResponseDto response = userSearchService.searchByPublicId(
                loginUserId,
                "TCAT-00000001"
        );

        // then
        assertThat(response.friendStatus()).isEqualTo(UserSearchFriendStatus.SELF);

        verify(userBlockService, never()).isBlockedBetween(anyLong(), anyLong());
        verify(friendService, never()).areFriends(anyLong(), anyLong());
        verify(friendRequestRepository, never()).findBetweenUsersByStatus(anyLong(), anyLong(), any(FriendRequestStatus.class));
    }

    @Test
    @DisplayName("이미 친구인 사용자 상태 반환")
    void searchFriend() {
        // given
        Long loginUserId = 1L;
        User targetUser = createUser(2L, "friend@example.com", "friend", "TCAT-00000002");

        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(targetUser));
        when(userProfileQueryService.getSummaryByUser(targetUser)).thenReturn(createSummary(targetUser));
        when(userBlockService.isBlockedBetween(loginUserId, 2L)).thenReturn(false);
        when(friendService.areFriends(loginUserId, 2L)).thenReturn(true);

        // when
        UserSearchResponseDto response = userSearchService.searchByPublicId(
                loginUserId,
                "TCAT-00000002"
        );

        // then
        assertThat(response.friendStatus()).isEqualTo(UserSearchFriendStatus.FRIEND);

        verify(friendRequestRepository, never()).findBetweenUsersByStatus(anyLong(), anyLong(), any(FriendRequestStatus.class));
    }

    @Test
    @DisplayName("내가 보낸 친구 요청 상태 반환")
    void searchRequestSent() {
        // given
        Long loginUserId = 1L;
        User loginUser = createUser(1L, "me@example.com", "me", "TCAT-00000001");
        User targetUser = createUser(2L, "target@example.com", "target", "TCAT-00000002");
        FriendRequest friendRequest = FriendRequest.create(loginUser, targetUser);

        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(targetUser));
        when(userProfileQueryService.getSummaryByUser(targetUser)).thenReturn(createSummary(targetUser));
        when(userBlockService.isBlockedBetween(loginUserId, 2L)).thenReturn(false);
        when(friendService.areFriends(loginUserId, 2L)).thenReturn(false);
        when(friendRequestRepository.findBetweenUsersByStatus(
                loginUserId,
                2L,
                FriendRequestStatus.PENDING
        )).thenReturn(Optional.of(friendRequest));

        // when
        UserSearchResponseDto response = userSearchService.searchByPublicId(
                loginUserId,
                "TCAT-00000002"
        );

        // then
        assertThat(response.friendStatus()).isEqualTo(UserSearchFriendStatus.REQUEST_SENT);
    }

    @Test
    @DisplayName("내가 받은 친구 요청 상태 반환")
    void searchRequestReceived() {
        // given
        Long loginUserId = 1L;
        User loginUser = createUser(1L, "me@example.com", "me", "TCAT-00000001");
        User targetUser = createUser(2L, "target@example.com", "target", "TCAT-00000002");
        FriendRequest friendRequest = FriendRequest.create(targetUser, loginUser);

        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(targetUser));
        when(userProfileQueryService.getSummaryByUser(targetUser)).thenReturn(createSummary(targetUser));
        when(userBlockService.isBlockedBetween(loginUserId, 2L)).thenReturn(false);
        when(friendService.areFriends(loginUserId, 2L)).thenReturn(false);
        when(friendRequestRepository.findBetweenUsersByStatus(
                loginUserId,
                2L,
                FriendRequestStatus.PENDING
        )).thenReturn(Optional.of(friendRequest));

        // when
        UserSearchResponseDto response = userSearchService.searchByPublicId(
                loginUserId,
                "TCAT-00000002"
        );

        // then
        assertThat(response.friendStatus()).isEqualTo(UserSearchFriendStatus.REQUEST_RECEIVED);
    }

    @Test
    @DisplayName("차단 관계 상태 반환")
    void searchBlocked() {
        // given
        Long loginUserId = 1L;
        User targetUser = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");

        when(userRepository.findByPublicId("TCAT-00000002")).thenReturn(Optional.of(targetUser));
        when(userProfileQueryService.getSummaryByUser(targetUser)).thenReturn(createSummary(targetUser));
        when(userBlockService.isBlockedBetween(loginUserId, 2L)).thenReturn(true);

        // when
        UserSearchResponseDto response = userSearchService.searchByPublicId(
                loginUserId,
                "TCAT-00000002"
        );

        // then
        assertThat(response.friendStatus()).isEqualTo(UserSearchFriendStatus.BLOCKED);

        verify(friendService, never()).areFriends(anyLong(), anyLong());
        verify(friendRequestRepository, never()).findBetweenUsersByStatus(anyLong(), anyLong(), any(FriendRequestStatus.class));
    }

    @Test
    @DisplayName("존재하지 않는 publicId 처리")
    void failWhenPublicIdDoesNotExist() {
        // given
        Long loginUserId = 1L;

        when(userRepository.findByPublicId("UNKNOWN")).thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userSearchService.searchByPublicId(loginUserId, "UNKNOWN"))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("PUBLIC_ID_NOT_FOUND")
                );
    }

    @Test
    @DisplayName("publicId가 비어 있으면 실패")
    void failWhenPublicIdIsBlank() {
        // given
        Long loginUserId = 1L;

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userSearchService.searchByPublicId(loginUserId, " "))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("PUBLIC_ID_REQUIRED")
                );
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 검색할 수 없다")
    void failWhenUnauthenticated() {
        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> userSearchService.searchByPublicId(null, "TCAT-00000002"))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UNAUTHORIZED")
                );
    }

    private UserSummaryProfileResponseDto createSummary(User user) {
        return new UserSummaryProfileResponseDto(
                user.getId(),
                user.getPublicId(),
                user.getUsername(),
                null
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
