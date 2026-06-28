package jp.co.translacat.domain.user.block.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.user.block.entity.QUserBlock;
import jp.co.translacat.domain.user.block.entity.UserBlock;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class UserBlockRepositoryImpl implements UserBlockRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<UserBlock> findByBlockerAndBlocked(
            Long blockerUserId,
            Long blockedUserId
    ) {
        QUserBlock userBlock = QUserBlock.userBlock;

        UserBlock result = queryFactory
                .selectFrom(userBlock)
                .where(blockerAndBlocked(userBlock, blockerUserId, blockedUserId))
                .fetchFirst();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<UserBlock> findActiveByBlockerAndBlocked(
            Long blockerUserId,
            Long blockedUserId
    ) {
        QUserBlock userBlock = QUserBlock.userBlock;

        UserBlock result = queryFactory
                .selectFrom(userBlock)
                .where(
                        userBlock.deleted.isFalse(),
                        blockerAndBlocked(userBlock, blockerUserId, blockedUserId)
                )
                .fetchFirst();

        return Optional.ofNullable(result);
    }

    @Override
    public boolean existsActiveByBlockerAndBlocked(
            Long blockerUserId,
            Long blockedUserId
    ) {
        QUserBlock userBlock = QUserBlock.userBlock;

        Integer result = queryFactory
                .selectOne()
                .from(userBlock)
                .where(
                        userBlock.deleted.isFalse(),
                        blockerAndBlocked(userBlock, blockerUserId, blockedUserId)
                )
                .fetchFirst();

        return result != null;
    }

    @Override
    public boolean existsActiveBlockBetweenUsers(
            Long userId1,
            Long userId2
    ) {
        QUserBlock userBlock = QUserBlock.userBlock;

        Integer result = queryFactory
                .selectOne()
                .from(userBlock)
                .where(
                        userBlock.deleted.isFalse(),
                        blockBetweenUsers(userBlock, userId1, userId2)
                )
                .fetchFirst();

        return result != null;
    }

    @Override
    public List<UserBlock> findActiveBlocksByBlockerUserId(Long blockerUserId) {
        QUserBlock userBlock = QUserBlock.userBlock;

        return queryFactory
                .selectFrom(userBlock)
                .where(
                        userBlock.deleted.isFalse(),
                        userBlock.blockerUser.id.eq(blockerUserId)
                )
                .orderBy(userBlock.createdAt.desc())
                .fetch();
    }

    private BooleanExpression blockBetweenUsers(
            QUserBlock userBlock,
            Long userId1,
            Long userId2
    ) {
        return blockerAndBlocked(userBlock, userId1, userId2)
                .or(blockerAndBlocked(userBlock, userId2, userId1));
    }

    private BooleanExpression blockerAndBlocked(
            QUserBlock userBlock,
            Long blockerUserId,
            Long blockedUserId
    ) {
        return userBlock.blockerUser.id.eq(blockerUserId)
                .and(userBlock.blockedUser.id.eq(blockedUserId));
    }
}
