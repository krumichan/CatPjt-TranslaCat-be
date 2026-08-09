package jp.co.translacat.domain.chat.ai.repository;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiActivity;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static jp.co.translacat.domain.chat.ai.entity.QChatRoomAiActivity.chatRoomAiActivity;
import static jp.co.translacat.domain.chat.ai.entity.QChatRoomAiMember.chatRoomAiMember;
import static jp.co.translacat.domain.chat.ai.entity.QChatRoomAiSetting.chatRoomAiSetting;
import static jp.co.translacat.domain.chat.room.entity.QChatRoom.chatRoom;

@Repository
@RequiredArgsConstructor
public class ChatRoomAiActivityRepositoryImpl
        implements ChatRoomAiActivityRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<ChatRoomAiActivity> findByIdForUpdate(Long activityId) {
        ChatRoomAiActivity activity = queryFactory
                .selectFrom(chatRoomAiActivity)
                .join(chatRoomAiActivity.chatRoom, chatRoom)
                .fetchJoin()
                .where(chatRoomAiActivity.id.eq(activityId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(activity);
    }

    @Override
    public Optional<ChatRoomAiActivity> findByChatRoomIdForUpdate(Long chatRoomId) {
        ChatRoomAiActivity activity = queryFactory
                .selectFrom(chatRoomAiActivity)
                .join(chatRoomAiActivity.chatRoom, chatRoom)
                .fetchJoin()
                .where(chatRoomAiActivity.chatRoom.id.eq(chatRoomId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(activity);
    }

    @Override
    public List<Long> findDueActivityIds(
            LocalDateTime now,
            Collection<ChatRoomType> roomTypes,
            Pageable pageable
    ) {
        if (roomTypes == null || roomTypes.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .select(chatRoomAiActivity.id)
                .from(chatRoomAiActivity)
                .join(chatRoomAiActivity.chatRoom, chatRoom)
                .where(
                        chatRoomAiActivity.revivalStopped.isFalse(),
                        chatRoomAiActivity.nextRevivalAt.isNotNull(),
                        chatRoomAiActivity.nextRevivalAt.loe(now),

                        chatRoomAiActivity.claimExpiresAt.isNull()
                                .or(chatRoomAiActivity.claimExpiresAt.loe(now)),

                        chatRoom.active.isTrue(),
                        chatRoom.deletedAt.isNull(),
                        chatRoom.roomType.in(roomTypes),

                        JPAExpressions
                                .selectOne()
                                .from(chatRoomAiSetting)
                                .where(
                                        chatRoomAiSetting.chatRoom.eq(chatRoom),
                                        chatRoomAiSetting.revivalEnabled.isTrue()
                                )
                                .exists(),

                        JPAExpressions
                                .selectOne()
                                .from(chatRoomAiMember)
                                .where(
                                        chatRoomAiMember.chatRoom.eq(chatRoom),
                                        chatRoomAiMember.active.isTrue(),
                                        chatRoomAiMember.deletedAt.isNull(),
                                        chatRoomAiMember.aiAgent.active.isTrue(),
                                        chatRoomAiMember.aiAgent.deletedAt.isNull()
                                )
                                .exists()
                )
                .orderBy(
                        chatRoomAiActivity.nextRevivalAt.asc(),
                        chatRoomAiActivity.id.asc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }
}