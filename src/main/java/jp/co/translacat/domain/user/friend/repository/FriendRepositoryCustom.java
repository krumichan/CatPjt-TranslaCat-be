package jp.co.translacat.domain.user.friend.repository;

import jp.co.translacat.domain.user.friend.entity.Friend;

import java.util.List;
import java.util.Optional;

public interface FriendRepositoryCustom {

    Optional<Friend> findByUserIds(
            Long userId1,
            Long userId2
    );

    Optional<Friend> findActiveByUserIds(
            Long userId1,
            Long userId2
    );

    boolean existsActiveByUserIds(
            Long userId1,
            Long userId2
    );

    List<Friend> findActiveFriendsByUserId(Long userId);
}
