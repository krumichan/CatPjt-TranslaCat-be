package jp.co.translacat.domain.user.friend.request.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.entity.QFriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class FriendRequestRepositoryImpl implements FriendRequestRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<FriendRequest> findBetweenUsersByStatus(
            Long userId1,
            Long userId2,
            FriendRequestStatus status
    ) {
        QFriendRequest friendRequest = QFriendRequest.friendRequest;

        FriendRequest result = queryFactory
                .selectFrom(friendRequest)
                .where(
                        friendRequest.deleted.isFalse(),
                        friendRequest.status.eq(status),
                        betweenUsers(friendRequest, userId1, userId2)
                )
                .fetchFirst();

        return Optional.ofNullable(result);
    }

    @Override
    public boolean existsPendingBetweenUsers(
            Long userId1,
            Long userId2
    ) {
        QFriendRequest friendRequest = QFriendRequest.friendRequest;

        Integer result = queryFactory
                .selectOne()
                .from(friendRequest)
                .where(
                        friendRequest.deleted.isFalse(),
                        friendRequest.status.eq(FriendRequestStatus.PENDING),
                        betweenUsers(friendRequest, userId1, userId2)
                )
                .fetchFirst();

        return result != null;
    }

    private BooleanExpression betweenUsers(
            QFriendRequest friendRequest,
            Long userId1,
            Long userId2
    ) {
        return requesterAndReceiver(friendRequest, userId1, userId2)
                .or(requesterAndReceiver(friendRequest, userId2, userId1));
    }

    private BooleanExpression requesterAndReceiver(
            QFriendRequest friendRequest,
            Long requesterUserId,
            Long receiverUserId
    ) {
        return friendRequest.requesterUser.id.eq(requesterUserId)
                .and(friendRequest.receiverUser.id.eq(receiverUserId));
    }
}