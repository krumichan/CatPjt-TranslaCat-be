package jp.co.translacat.domain.chat.notification.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import jp.co.translacat.domain.chat.notification.entity.QChatNotification;
import jp.co.translacat.domain.chat.room.entity.QChatRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatNotificationRepositoryImpl
        implements ChatNotificationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ChatNotification> findActivityPage(
            Long recipientUserId,
            boolean onlyUnread,
            Long cursorId,
            int limit
    ) {
        if (recipientUserId == null || limit <= 0) {
            return List.of();
        }

        QChatNotification notification =
                QChatNotification.chatNotification;
        QChatRoom chatRoom = new QChatRoom("notificationChatRoom");
        BooleanBuilder where = new BooleanBuilder()
                .and(notification.recipientUser.id.eq(recipientUserId))
                .and(notification.deletedAt.isNull());

        if (onlyUnread) {
            where.and(notification.read.isFalse());
        }
        if (cursorId != null) {
            where.and(notification.id.lt(cursorId));
        }

        return queryFactory
                .selectFrom(notification)
                .leftJoin(notification.chatRoom, chatRoom)
                .fetchJoin()
                .where(where)
                .orderBy(notification.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public long countUnreadActivities(Long recipientUserId) {
        if (recipientUserId == null) {
            return 0L;
        }

        QChatNotification notification =
                QChatNotification.chatNotification;
        Long count = queryFactory
                .select(notification.count())
                .from(notification)
                .where(
                        notification.recipientUser.id.eq(recipientUserId),
                        notification.read.isFalse(),
                        notification.deletedAt.isNull()
                )
                .fetchOne();

        return count != null ? count : 0L;
    }

    @Override
    public long markAllReadByRecipientUserId(
            Long recipientUserId,
            LocalDateTime readAt
    ) {
        if (recipientUserId == null || readAt == null) {
            return 0L;
        }

        QChatNotification notification =
                QChatNotification.chatNotification;
        return queryFactory
                .update(notification)
                .set(notification.read, true)
                .set(notification.readAt, readAt)
                .where(
                        notification.recipientUser.id.eq(recipientUserId),
                        notification.read.isFalse(),
                        notification.deletedAt.isNull()
                )
                .execute();
    }
}
