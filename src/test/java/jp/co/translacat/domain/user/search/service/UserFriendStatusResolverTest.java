package jp.co.translacat.domain.user.search.service;

import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.search.enums.UserSearchFriendStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserFriendStatusResolverTest {

    @Mock
    private UserBlockService userBlockService;

    @Mock
    private FriendService friendService;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private User targetUser;

    @Mock
    private FriendRequest friendRequest;

    @Test
    void resolveReturnsSelf() {
        when(targetUser.getId()).thenReturn(1L);

        UserSearchFriendStatus result =
                UserFriendStatusResolver.resolve(
                        1L,
                        targetUser,
                        userBlockService,
                        friendService,
                        friendRequestRepository
                );

        assertThat(result).isEqualTo(UserSearchFriendStatus.SELF);
        verifyNoInteractions(
                userBlockService,
                friendService,
                friendRequestRepository
        );
    }

    @Test
    void resolveReturnsBlockedBeforeFriendCheck() {
        when(targetUser.getId()).thenReturn(2L);
        when(userBlockService.isBlockedBetween(1L, 2L))
                .thenReturn(true);

        UserSearchFriendStatus result =
                UserFriendStatusResolver.resolve(
                        1L,
                        targetUser,
                        userBlockService,
                        friendService,
                        friendRequestRepository
                );

        assertThat(result).isEqualTo(UserSearchFriendStatus.BLOCKED);
        verifyNoInteractions(friendService, friendRequestRepository);
    }

    @Test
    void resolveReturnsFriend() {
        when(targetUser.getId()).thenReturn(2L);
        when(userBlockService.isBlockedBetween(1L, 2L))
                .thenReturn(false);
        when(friendService.areFriends(1L, 2L))
                .thenReturn(true);

        UserSearchFriendStatus result =
                UserFriendStatusResolver.resolve(
                        1L,
                        targetUser,
                        userBlockService,
                        friendService,
                        friendRequestRepository
                );

        assertThat(result).isEqualTo(UserSearchFriendStatus.FRIEND);
        verifyNoInteractions(friendRequestRepository);
    }

    @Test
    void resolveReturnsRequestSent() {
        when(targetUser.getId()).thenReturn(2L);
        when(userBlockService.isBlockedBetween(1L, 2L))
                .thenReturn(false);
        when(friendService.areFriends(1L, 2L))
                .thenReturn(false);
        when(friendRequestRepository.findBetweenUsersByStatus(
                1L,
                2L,
                FriendRequestStatus.PENDING
        )).thenReturn(Optional.of(friendRequest));
        when(friendRequest.isRequestedBy(1L)).thenReturn(true);

        UserSearchFriendStatus result =
                UserFriendStatusResolver.resolve(
                        1L,
                        targetUser,
                        userBlockService,
                        friendService,
                        friendRequestRepository
                );

        assertThat(result)
                .isEqualTo(UserSearchFriendStatus.REQUEST_SENT);
    }

    @Test
    void resolveReturnsRequestReceived() {
        when(targetUser.getId()).thenReturn(2L);
        when(userBlockService.isBlockedBetween(1L, 2L))
                .thenReturn(false);
        when(friendService.areFriends(1L, 2L))
                .thenReturn(false);
        when(friendRequestRepository.findBetweenUsersByStatus(
                1L,
                2L,
                FriendRequestStatus.PENDING
        )).thenReturn(Optional.of(friendRequest));
        when(friendRequest.isRequestedBy(1L)).thenReturn(false);
        when(friendRequest.isReceivedBy(1L)).thenReturn(true);

        UserSearchFriendStatus result =
                UserFriendStatusResolver.resolve(
                        1L,
                        targetUser,
                        userBlockService,
                        friendService,
                        friendRequestRepository
                );

        assertThat(result)
                .isEqualTo(UserSearchFriendStatus.REQUEST_RECEIVED);
    }

    @Test
    void resolveReturnsNone() {
        when(targetUser.getId()).thenReturn(2L);
        when(userBlockService.isBlockedBetween(1L, 2L))
                .thenReturn(false);
        when(friendService.areFriends(1L, 2L))
                .thenReturn(false);
        when(friendRequestRepository.findBetweenUsersByStatus(
                1L,
                2L,
                FriendRequestStatus.PENDING
        )).thenReturn(Optional.empty());

        UserSearchFriendStatus result =
                UserFriendStatusResolver.resolve(
                        1L,
                        targetUser,
                        userBlockService,
                        friendService,
                        friendRequestRepository
                );

        assertThat(result).isEqualTo(UserSearchFriendStatus.NONE);
    }
}
