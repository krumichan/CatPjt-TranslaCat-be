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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ChatUnreadCountRepositoryImpl
        implements ChatUnreadCountRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Map<Long, Long> countUnreadByRoomIds(
            Long userId,
            List<Long> chatRoomIds
    ) {
        if (userId == null
                || chatRoomIds == null
                || chatRoomIds.isEmpty()) {
            return Map.of();
        }

        QChatRoomMember member =
                new QChatRoomMember("unreadTargetMember");
        QChatMessage message =
                new QChatMessage("unreadTargetMessage");
        QUser sender = new QUser("unreadMessageSender");
        NumberExpression<Long> unreadCount = message.id.count();

        List<Tuple> rows = queryFactory
                .select(member.chatRoom.id, unreadCount)
                .from(member)
                .join(message)
                .on(message.chatRoom.eq(member.chatRoom))
                .leftJoin(message.senderUser, sender)
                .where(
                        member.user.id.eq(userId),
                        member.chatRoom.id.in(chatRoomIds),
                        member.active.isTrue(),
                        member.deletedAt.isNull(),
                        message.status.eq(ChatMessageStatus.SENT),
                        message.deletedAt.isNull(),
                        message.messageType.ne(ChatMessageType.SYSTEM),
                        message.createdAt.goe(member.joinedAt),
                        message.senderType.eq(ChatMessageSenderType.AI)
                                .or(message.senderType
                                        .eq(ChatMessageSenderType.USER)
                                        .and(sender.id.ne(userId))),
                        member.lastReadMessageId.isNull()
                                .or(message.id.gt(
                                        member.lastReadMessageId
                                ))
                )
                .groupBy(member.chatRoom.id)
                .fetch();

        Map<Long, Long> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            Long chatRoomId = row.get(member.chatRoom.id);
            Long count = row.get(unreadCount);
            if (chatRoomId != null) {
                result.put(chatRoomId, count != null ? count : 0L);
            }
        }
        return result;
    }
}
