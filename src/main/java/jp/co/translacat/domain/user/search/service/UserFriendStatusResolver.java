package jp.co.translacat.domain.user.search.service;

import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.search.enums.UserSearchFriendStatus;

public final class UserFriendStatusResolver {

    private UserFriendStatusResolver() {
    }

    public static UserSearchFriendStatus resolve(
            Long loginUserId,
            User targetUser,
            UserBlockService userBlockService,
            FriendService friendService,
            FriendRequestRepository friendRequestRepository
    ) {
        Long targetUserId = targetUser.getId();

        if (targetUserId.equals(loginUserId)) {
            return UserSearchFriendStatus.SELF;
        }

        if (userBlockService.isBlockedBetween(loginUserId, targetUserId)) {
            return UserSearchFriendStatus.BLOCKED;
        }

        if (friendService.areFriends(loginUserId, targetUserId)) {
            return UserSearchFriendStatus.FRIEND;
        }

        return friendRequestRepository.findBetweenUsersByStatus(
                        loginUserId,
                        targetUserId,
                        FriendRequestStatus.PENDING
                )
                .map(friendRequest -> resolvePendingRequestStatus(
                        friendRequest,
                        loginUserId
                ))
                .orElse(UserSearchFriendStatus.NONE);
    }

    private static UserSearchFriendStatus resolvePendingRequestStatus(
            FriendRequest friendRequest,
            Long loginUserId
    ) {
        if (friendRequest.isRequestedBy(loginUserId)) {
            return UserSearchFriendStatus.REQUEST_SENT;
        }

        if (friendRequest.isReceivedBy(loginUserId)) {
            return UserSearchFriendStatus.REQUEST_RECEIVED;
        }

        return UserSearchFriendStatus.NONE;
    }
}
