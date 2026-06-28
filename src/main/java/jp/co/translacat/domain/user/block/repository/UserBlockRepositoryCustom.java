package jp.co.translacat.domain.user.block.repository;

import jp.co.translacat.domain.user.block.entity.UserBlock;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepositoryCustom {

    Optional<UserBlock> findByBlockerAndBlocked(
            Long blockerUserId,
            Long blockedUserId
    );

    Optional<UserBlock> findActiveByBlockerAndBlocked(
            Long blockerUserId,
            Long blockedUserId
    );

    boolean existsActiveByBlockerAndBlocked(
            Long blockerUserId,
            Long blockedUserId
    );

    boolean existsActiveBlockBetweenUsers(
            Long userId1,
            Long userId2
    );

    List<UserBlock> findActiveBlocksByBlockerUserId(Long blockerUserId);
}
