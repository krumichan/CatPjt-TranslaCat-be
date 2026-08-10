package jp.co.translacat.domain.chat.openchat.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.chat.member.entity.QChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.message.entity.QChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatRoomStatus;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.profile.entity.QOpenChatMemberProfile;
import jp.co.translacat.domain.chat.room.entity.QChatRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static jp.co.translacat.domain.chat.openchat.entity.QOpenChatRoom.openChatRoom;

@Repository
@RequiredArgsConstructor
public class OpenChatRoomRepositoryImpl
        implements OpenChatRoomRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OpenChatRoom> findPublicActivePage(
            String keyword,
            Long cursorId,
            int limit
    ) {
        QChatRoom room = new QChatRoom("openChatListRoom");

        return queryFactory
                .selectFrom(openChatRoom)
                .join(openChatRoom.chatRoom, room)
                .fetchJoin()
                .where(
                        openChatRoom.visibility.eq(
                                OpenChatVisibility.PUBLIC
                        ),
                        openChatRoom.status.eq(
                                OpenChatRoomStatus.ACTIVE
                        ),
                        room.active.isTrue(),
                        room.deletedAt.isNull(),
                        cursorId != null
                                ? room.id.lt(cursorId)
                                : null,
                        keywordCondition(room, keyword)
                )
                .orderBy(room.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public Optional<OpenChatRoom> findByChatRoomId(
            Long chatRoomId
    ) {
        QChatRoom room = new QChatRoom("openChatDetailRoom");

        OpenChatRoom result = queryFactory
                .selectFrom(openChatRoom)
                .join(openChatRoom.chatRoom, room)
                .fetchJoin()
                .where(
                        room.id.eq(chatRoomId),
                        room.active.isTrue(),
                        room.deletedAt.isNull()
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<OpenChatRoom> findByChatRoomIdForUpdate(
            Long chatRoomId
    ) {
        QChatRoom room = new QChatRoom("openChatLockedRoom");

        OpenChatRoom result = queryFactory
                .selectFrom(openChatRoom)
                .join(openChatRoom.chatRoom, room)
                .fetchJoin()
                .where(
                        room.id.eq(chatRoomId),
                        room.active.isTrue(),
                        room.deletedAt.isNull()
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Map<Long, Long> countActiveMembers(
            List<Long> chatRoomIds
    ) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) {
            return Map.of();
        }

        QChatRoomMember member =
                new QChatRoomMember("openChatCountMember");
        NumberExpression<Long> memberCount = member.id.count();

        List<Tuple> rows = queryFactory
                .select(member.chatRoom.id, memberCount)
                .from(member)
                .where(
                        member.chatRoom.id.in(chatRoomIds),
                        member.active.isTrue(),
                        member.deletedAt.isNull()
                )
                .groupBy(member.chatRoom.id)
                .fetch();

        Map<Long, Long> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            Long roomId = row.get(member.chatRoom.id);
            Long count = row.get(memberCount);
            if (roomId != null) {
                result.put(roomId, count != null ? count : 0L);
            }
        }
        return result;
    }

    @Override
    public Set<Long> findJoinedRoomIds(
            Long userId,
            List<Long> chatRoomIds
    ) {
        if (userId == null
                || chatRoomIds == null
                || chatRoomIds.isEmpty()) {
            return Set.of();
        }

        QChatRoomMember member =
                new QChatRoomMember("openChatJoinedMember");

        return new LinkedHashSet<>(queryFactory
                .select(member.chatRoom.id)
                .from(member)
                .where(
                        member.user.id.eq(userId),
                        member.chatRoom.id.in(chatRoomIds),
                        member.active.isTrue(),
                        member.deletedAt.isNull()
                )
                .fetch());
    }

    @Override
    public Set<Long> findActiveRoomIds(List<Long> chatRoomIds) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) {
            return Set.of();
        }

        return new LinkedHashSet<>(queryFactory
                .select(openChatRoom.chatRoom.id)
                .from(openChatRoom)
                .where(
                        openChatRoom.chatRoom.id.in(chatRoomIds),
                        openChatRoom.status.eq(OpenChatRoomStatus.ACTIVE)
                )
                .fetch());
    }

    @Override
    public Map<Long, OpenChatMemberProfileQueryRow>
    findOwnerProfiles(List<Long> chatRoomIds) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) {
            return Map.of();
        }

        QOpenChatMemberProfile profile =
                new QOpenChatMemberProfile("openChatOwnerProfile");
        QChatRoomMember member =
                new QChatRoomMember("openChatOwnerMember");

        List<OpenChatMemberProfileQueryRow> rows = queryFactory
                .select(Projections.constructor(
                        OpenChatMemberProfileQueryRow.class,
                        member.chatRoom.id,
                        member.id,
                        profile.memberCode,
                        profile.nickname,
                        profile.profileImageObjectKey,
                        member.role,
                        member.active,
                        member.joinedAt
                ))
                .from(profile)
                .join(profile.chatRoomMember, member)
                .where(
                        member.chatRoom.id.in(chatRoomIds),
                        member.role.eq(ChatRoomMemberRole.OWNER),
                        member.active.isTrue(),
                        member.deletedAt.isNull()
                )
                .fetch();

        Map<Long, OpenChatMemberProfileQueryRow> result =
                new LinkedHashMap<>();
        for (OpenChatMemberProfileQueryRow row : rows) {
            result.putIfAbsent(row.chatRoomId(), row);
        }
        return result;
    }

    @Override
    public Optional<OpenChatMemberProfileQueryRow>
    findMyProfile(
            Long chatRoomId,
            Long userId
    ) {
        if (chatRoomId == null || userId == null) {
            return Optional.empty();
        }

        QOpenChatMemberProfile profile =
                new QOpenChatMemberProfile("openChatMyProfile");
        QChatRoomMember member =
                new QChatRoomMember("openChatMyMember");

        OpenChatMemberProfileQueryRow row = queryFactory
                .select(Projections.constructor(
                        OpenChatMemberProfileQueryRow.class,
                        member.chatRoom.id,
                        member.id,
                        profile.memberCode,
                        profile.nickname,
                        profile.profileImageObjectKey,
                        member.role,
                        member.active,
                        member.joinedAt
                ))
                .from(profile)
                .join(profile.chatRoomMember, member)
                .where(
                        member.chatRoom.id.eq(chatRoomId),
                        member.user.id.eq(userId),
                        member.deletedAt.isNull()
                )
                .fetchOne();

        return Optional.ofNullable(row);
    }

    @Override
    public Map<Long, LocalDateTime> findLastActivityAt(
            List<Long> chatRoomIds
    ) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) {
            return Map.of();
        }

        QChatMessage message =
                new QChatMessage("openChatLastActivityMessage");
        DateTimeExpression<LocalDateTime> lastActivityAt =
                message.createdAt.max();

        List<Tuple> rows = queryFactory
                .select(message.chatRoom.id, lastActivityAt)
                .from(message)
                .where(
                        message.chatRoom.id.in(chatRoomIds),
                        message.status.eq(ChatMessageStatus.SENT),
                        message.deletedAt.isNull()
                )
                .groupBy(message.chatRoom.id)
                .fetch();

        Map<Long, LocalDateTime> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            Long roomId = row.get(message.chatRoom.id);
            LocalDateTime activityAt = row.get(lastActivityAt);
            if (roomId != null && activityAt != null) {
                result.put(roomId, activityAt);
            }
        }
        return result;
    }

    private BooleanExpression keywordCondition(
            QChatRoom room,
            String keyword
    ) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return room.name.containsIgnoreCase(keyword)
                .or(room.description.containsIgnoreCase(keyword));
    }
}
