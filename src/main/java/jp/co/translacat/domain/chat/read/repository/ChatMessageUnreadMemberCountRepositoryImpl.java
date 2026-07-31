package jp.co.translacat.domain.chat.read.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.chat.member.entity.QChatRoomMember;
import jp.co.translacat.domain.chat.message.entity.QChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.enums.ChatMessageType;
import jp.co.translacat.domain.user.entity.QUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ChatMessageUnreadMemberCountRepositoryImpl
        implements ChatMessageUnreadMemberCountRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Map<Long, Long> countUnreadMembersByMessageIds(
            Collection<Long> messageIds
    ) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }

        QChatMessage message =
                new QChatMessage("readCountMessage");
        QChatRoomMember member =
                new QChatRoomMember("readCountMember");
        QUser sender = new QUser("readCountSender");

        NumberExpression<Long> unreadMemberCount =
                member.id.count();

        List<Tuple> rows = queryFactory
                .select(message.id, unreadMemberCount)
                .from(message)
                .join(member)
                .on(member.chatRoom.eq(message.chatRoom))
                .leftJoin(message.senderUser, sender)
                .where(
                        message.id.in(messageIds),
                        message.status.eq(ChatMessageStatus.SENT),
                        message.deletedAt.isNull(),
                        message.messageType.ne(ChatMessageType.SYSTEM),
                        message.senderType.in(
                                ChatMessageSenderType.USER,
                                ChatMessageSenderType.AI
                        ),
                        member.active.isTrue(),
                        member.deletedAt.isNull(),
                        member.joinedAt.loe(message.createdAt),
                        sender.id.isNull()
                                .or(sender.id.ne(member.user.id)),
                        member.lastReadMessageId.isNull()
                                .or(member.lastReadMessageId.lt(message.id))
                )
                .groupBy(message.id)
                .fetch();

        Map<Long, Long> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            Long messageId = row.get(message.id);
            Long count = row.get(unreadMemberCount);
            if (messageId != null) {
                result.put(messageId, count != null ? count : 0L);
            }
        }
        return result;
    }
}
