package jp.co.translacat.domain.user.friend.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.user.friend.entity.Friend;
import jp.co.translacat.domain.user.friend.entity.QFriend;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class FriendRepositoryImpl implements FriendRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Friend> findByUserIds(
            Long userId1,
            Long userId2
    ) {
        QFriend friend = QFriend.friend;

        Friend result = queryFactory
                .selectFrom(friend)
                .where(betweenUsers(friend, userId1, userId2))
                .fetchFirst();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<Friend> findActiveByUserIds(
            Long userId1,
            Long userId2
    ) {
        QFriend friend = QFriend.friend;

        Friend result = queryFactory
                .selectFrom(friend)
                .where(
                        friend.deleted.isFalse(),
                        betweenUsers(friend, userId1, userId2)
                )
                .fetchFirst();

        return Optional.ofNullable(result);
    }

    @Override
    public boolean existsActiveByUserIds(
            Long userId1,
            Long userId2
    ) {
        QFriend friend = QFriend.friend;

        Integer result = queryFactory
                .selectOne()
                .from(friend)
                .where(
                        friend.deleted.isFalse(),
                        betweenUsers(friend, userId1, userId2)
                )
                .fetchFirst();

        return result != null;
    }

    @Override
    public List<Friend> findActiveFriendsByUserId(Long userId) {
        QFriend friend = QFriend.friend;

        return queryFactory
                .selectFrom(friend)
                .where(
                        friend.deleted.isFalse(),
                        friend.userLow.id.eq(userId)
                                .or(friend.userHigh.id.eq(userId))
                )
                .orderBy(friend.createdAt.desc())
                .fetch();
    }

    private BooleanExpression betweenUsers(
            QFriend friend,
            Long userId1,
            Long userId2
    ) {
        return friend.userLow.id.eq(userId1)
                .and(friend.userHigh.id.eq(userId2))
                .or(
                        friend.userLow.id.eq(userId2)
                                .and(friend.userHigh.id.eq(userId1))
                );
    }
}
