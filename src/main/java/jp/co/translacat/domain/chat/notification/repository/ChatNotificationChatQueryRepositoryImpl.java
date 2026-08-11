package jp.co.translacat.domain.chat.notification.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.chat.member.entity.QChatRoomMember;
import jp.co.translacat.domain.chat.message.entity.QChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.enums.ChatMessageType;
import jp.co.translacat.domain.chat.notification.repository.projection.ChatNotificationRoomQueryRow;
import jp.co.translacat.domain.chat.notification.repository.projection.ChatNotificationUnreadSummary;
import jp.co.translacat.domain.chat.openchat.entity.QOpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatRoomStatus;
import jp.co.translacat.domain.chat.room.entity.QChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.user.entity.QUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatNotificationChatQueryRepositoryImpl
        implements ChatNotificationChatQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ChatNotificationRoomQueryRow> findUnreadChatRoomPage(
            Long userId,
            Long cursorMessageId,
            int limit
    ) {
        if (userId == null || limit <= 0) {
            return List.of();
        }

        QChatRoomMember member =
                new QChatRoomMember("notificationTargetMember");
        QChatRoom room =
                new QChatRoom("notificationTargetRoom");
        QChatMessage message =
                new QChatMessage("notificationTargetMessage");
        QOpenChatRoom openRoom =
                new QOpenChatRoom("notificationTargetOpenRoom");
        QUser sender =
                new QUser("notificationTargetSender");

        BooleanExpression unreadCondition = unreadCondition(
                userId,
                member,
                message,
                sender
        );
        NumberExpression<Long> latestMessageId = message.id.max();
        NumberExpression<Long> unreadCount =
                new com.querydsl.core.types.dsl.CaseBuilder()
                        .when(unreadCondition)
                        .then(1L)
                        .otherwise(0L)
                        .sum();
        NumberExpression<Long> firstUnreadMessageId =
                new com.querydsl.core.types.dsl.CaseBuilder()
                        .when(unreadCondition)
                        .then(message.id)
                        .otherwise(Long.MAX_VALUE)
                        .min();

        List<Tuple> tuples = queryFactory
                .select(
                        room.id,
                        room.roomType,
                        room.sourceType,
                        room.name,
                        latestMessageId,
                        unreadCount,
                        firstUnreadMessageId
                )
                .from(member)
                .join(member.chatRoom, room)
                .join(message)
                .on(message.chatRoom.eq(room))
                .leftJoin(message.senderUser, sender)
                .leftJoin(openRoom)
                .on(openRoom.chatRoom.eq(room))
                .where(
                        member.user.id.eq(userId),
                        member.active.isTrue(),
                        member.deletedAt.isNull(),
                        room.active.isTrue(),
                        room.deletedAt.isNull(),
                        activeOpenRoomCondition(room, openRoom),
                        message.status.eq(ChatMessageStatus.SENT),
                        message.deletedAt.isNull(),
                        message.messageType.ne(ChatMessageType.SYSTEM),
                        message.createdAt.goe(member.joinedAt)
                )
                .groupBy(
                        room.id,
                        room.roomType,
                        room.sourceType,
                        room.name
                )
                .having(
                        unreadCount.gt(0L),
                        cursorMessageId != null
                                ? latestMessageId.lt(cursorMessageId)
                                : null
                )
                .orderBy(
                        latestMessageId.desc(),
                        room.id.desc()
                )
                .limit(limit)
                .fetch();

        List<ChatNotificationRoomQueryRow> rows =
                new ArrayList<>(tuples.size());
        for (Tuple tuple : tuples) {
            Long roomId = tuple.get(room.id);
            Long latestId = tuple.get(latestMessageId);
            Long count = tuple.get(unreadCount);
            Long firstUnreadId = tuple.get(firstUnreadMessageId);
            if (roomId == null
                    || latestId == null
                    || count == null
                    || count <= 0L
                    || firstUnreadId == null) {
                continue;
            }
            rows.add(new ChatNotificationRoomQueryRow(
                    roomId,
                    tuple.get(room.roomType),
                    tuple.get(room.sourceType),
                    tuple.get(room.name),
                    latestId,
                    count,
                    firstUnreadId
            ));
        }
        return List.copyOf(rows);
    }

    @Override
    public ChatNotificationUnreadSummary summarizeUnreadChats(
            Long userId
    ) {
        if (userId == null) {
            return ChatNotificationUnreadSummary.empty();
        }

        QChatRoomMember member =
                new QChatRoomMember("notificationSummaryMember");
        QChatRoom room =
                new QChatRoom("notificationSummaryRoom");
        QChatMessage message =
                new QChatMessage("notificationSummaryMessage");
        QOpenChatRoom openRoom =
                new QOpenChatRoom("notificationSummaryOpenRoom");
        QUser sender =
                new QUser("notificationSummarySender");

        NumberExpression<Long> unreadMessageCountExpression =
                message.id.count();
        NumberExpression<Long> unreadRoomCountExpression =
                room.id.countDistinct();

        Tuple tuple = queryFactory
                .select(
                        unreadMessageCountExpression,
                        unreadRoomCountExpression
                )
                .from(member)
                .join(member.chatRoom, room)
                .join(message)
                .on(message.chatRoom.eq(room))
                .leftJoin(message.senderUser, sender)
                .leftJoin(openRoom)
                .on(openRoom.chatRoom.eq(room))
                .where(
                        member.user.id.eq(userId),
                        member.active.isTrue(),
                        member.deletedAt.isNull(),
                        room.active.isTrue(),
                        room.deletedAt.isNull(),
                        activeOpenRoomCondition(room, openRoom),
                        message.status.eq(ChatMessageStatus.SENT),
                        message.deletedAt.isNull(),
                        message.messageType.ne(ChatMessageType.SYSTEM),
                        message.createdAt.goe(member.joinedAt),
                        unreadCondition(userId, member, message, sender)
                )
                .fetchOne();

        if (tuple == null) {
            return ChatNotificationUnreadSummary.empty();
        }

        Long unreadMessageCount = tuple.get(
                unreadMessageCountExpression
        );
        Long unreadRoomCount = tuple.get(
                unreadRoomCountExpression
        );
        return new ChatNotificationUnreadSummary(
                unreadMessageCount != null ? unreadMessageCount : 0L,
                unreadRoomCount != null ? unreadRoomCount : 0L
        );
    }

    private BooleanExpression unreadCondition(
            Long userId,
            QChatRoomMember member,
            QChatMessage message,
            QUser sender
    ) {
        BooleanExpression afterReadCursor =
                member.lastReadMessageId.isNull()
                        .or(message.id.gt(member.lastReadMessageId));
        BooleanExpression unreadSender =
                message.senderType.eq(ChatMessageSenderType.AI)
                        .or(message.senderType
                                .eq(ChatMessageSenderType.USER)
                                .and(sender.id.ne(userId)));
        return afterReadCursor.and(unreadSender);
    }

    private BooleanExpression activeOpenRoomCondition(
            QChatRoom room,
            QOpenChatRoom openRoom
    ) {
        return room.roomType.ne(ChatRoomType.OPEN)
                .or(openRoom.status.eq(OpenChatRoomStatus.ACTIVE));
    }
}
