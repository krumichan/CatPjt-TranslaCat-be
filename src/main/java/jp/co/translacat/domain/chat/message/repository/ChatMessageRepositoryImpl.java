package jp.co.translacat.domain.chat.message.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.chat.message.entity.QChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.projection.ChatMessageAnchorWindowQueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl
        implements ChatMessageRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public ChatMessageAnchorWindowQueryResult findAnchorWindowIds(
            Long chatRoomId,
            Long anchorMessageId,
            LocalDateTime joinedAt,
            int beforeSize,
            int afterSize
    ) {
        if (chatRoomId == null
                || anchorMessageId == null
                || joinedAt == null
                || beforeSize < 0
                || afterSize < 0) {
            return ChatMessageAnchorWindowQueryResult.empty();
        }

        QChatMessage message = new QChatMessage("anchorWindowMessage");
        BooleanExpression baseCondition = baseCondition(
                message,
                chatRoomId,
                joinedAt
        );

        List<Long> previousFetched = queryFactory
                .select(message.id)
                .from(message)
                .where(
                        baseCondition,
                        message.id.lt(anchorMessageId)
                )
                .orderBy(message.id.desc())
                .limit((long) beforeSize + 1L)
                .fetch();

        boolean hasPrevious = previousFetched.size() > beforeSize;
        List<Long> previousIds = new ArrayList<>(
                previousFetched.subList(
                        0,
                        Math.min(previousFetched.size(), beforeSize)
                )
        );
        Collections.reverse(previousIds);

        List<Long> nextFetched = queryFactory
                .select(message.id)
                .from(message)
                .where(
                        baseCondition,
                        message.id.gt(anchorMessageId)
                )
                .orderBy(message.id.asc())
                .limit((long) afterSize + 1L)
                .fetch();

        boolean hasNext = nextFetched.size() > afterSize;
        List<Long> nextIds = new ArrayList<>(
                nextFetched.subList(
                        0,
                        Math.min(nextFetched.size(), afterSize)
                )
        );

        List<Long> messageIds = new ArrayList<>(
                previousIds.size() + 1 + nextIds.size()
        );
        messageIds.addAll(previousIds);
        messageIds.add(anchorMessageId);
        messageIds.addAll(nextIds);

        Long previousCursorId = hasPrevious
                ? (!previousIds.isEmpty()
                ? previousIds.getFirst()
                : anchorMessageId)
                : null;
        Long nextCursorId = hasNext
                ? (!nextIds.isEmpty()
                ? nextIds.getLast()
                : anchorMessageId)
                : null;

        return new ChatMessageAnchorWindowQueryResult(
                List.copyOf(messageIds),
                previousCursorId,
                hasPrevious,
                nextCursorId,
                hasNext
        );
    }

    @Override
    public List<Long> findNextMessageIds(
            Long chatRoomId,
            Long cursorMessageId,
            LocalDateTime joinedAt,
            int limit
    ) {
        if (chatRoomId == null
                || cursorMessageId == null
                || joinedAt == null
                || limit <= 0) {
            return List.of();
        }

        QChatMessage message = new QChatMessage("nextPageMessage");
        return queryFactory
                .select(message.id)
                .from(message)
                .where(
                        baseCondition(message, chatRoomId, joinedAt),
                        message.id.gt(cursorMessageId)
                )
                .orderBy(message.id.asc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression baseCondition(
            QChatMessage message,
            Long chatRoomId,
            LocalDateTime joinedAt
    ) {
        return message.chatRoom.id.eq(chatRoomId)
                .and(message.status.eq(ChatMessageStatus.SENT))
                .and(message.deletedAt.isNull())
                .and(message.createdAt.goe(joinedAt));
    }
}
