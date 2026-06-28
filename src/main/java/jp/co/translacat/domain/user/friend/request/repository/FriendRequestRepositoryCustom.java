package jp.co.translacat.domain.user.friend.request.repository;

import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;

import java.util.Optional;

public interface FriendRequestRepositoryCustom {

    Optional<FriendRequest> findBetweenUsersByStatus(
            Long userId1,
            Long userId2,
            FriendRequestStatus status
    );

    boolean existsPendingBetweenUsers(
            Long userId1,
            Long userId2
    );
}