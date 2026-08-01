package jp.co.translacat.domain.chat.openchat.ban.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.chat.member.entity.QChatRoomMember;
import jp.co.translacat.domain.chat.openchat.ban.entity.OpenChatBan;
import jp.co.translacat.domain.chat.openchat.ban.entity.QOpenChatBan;
import jp.co.translacat.domain.chat.openchat.profile.entity.QOpenChatMemberProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class OpenChatBanRepositoryImpl
        implements OpenChatBanRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public boolean existsActiveByRoomIdAndTargetUserId(
            Long roomId,
            Long targetUserId
    ) {
        if (roomId == null || targetUserId == null) {
            return false;
        }

        QOpenChatBan ban = QOpenChatBan.openChatBan;
        Integer result = queryFactory
                .selectOne()
                .from(ban)
                .where(
                        ban.chatRoom.id.eq(roomId),
                        ban.targetUser.id.eq(targetUserId),
                        ban.releasedAt.isNull()
                )
                .fetchFirst();

        return result != null;
    }

    @Override
    public Set<Long> findActiveBannedRoomIds(
            Long targetUserId,
            List<Long> roomIds
    ) {
        if (targetUserId == null
                || roomIds == null
                || roomIds.isEmpty()) {
            return Set.of();
        }

        QOpenChatBan ban = QOpenChatBan.openChatBan;
        return new LinkedHashSet<>(queryFactory
                .select(ban.chatRoom.id)
                .from(ban)
                .where(
                        ban.targetUser.id.eq(targetUserId),
                        ban.chatRoom.id.in(roomIds),
                        ban.releasedAt.isNull()
                )
                .fetch());
    }

    @Override
    public Optional<OpenChatBan>
    findActiveByRoomIdAndTargetUserIdForUpdate(
            Long roomId,
            Long targetUserId
    ) {
        if (roomId == null || targetUserId == null) {
            return Optional.empty();
        }

        QOpenChatBan ban = QOpenChatBan.openChatBan;
        OpenChatBan result = queryFactory
                .selectFrom(ban)
                .where(
                        ban.chatRoom.id.eq(roomId),
                        ban.targetUser.id.eq(targetUserId),
                        ban.releasedAt.isNull()
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<OpenChatBan> findActiveByIdAndRoomIdForUpdate(
            Long banId,
            Long roomId
    ) {
        if (banId == null || roomId == null) {
            return Optional.empty();
        }

        QOpenChatBan ban = QOpenChatBan.openChatBan;
        OpenChatBan result = queryFactory
                .selectFrom(ban)
                .where(
                        ban.id.eq(banId),
                        ban.chatRoom.id.eq(roomId),
                        ban.releasedAt.isNull()
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<OpenChatBanQueryRow> findActivePage(
            Long roomId,
            String keyword,
            Long cursorId,
            int limit
    ) {
        QOpenChatBan ban = QOpenChatBan.openChatBan;
        QChatRoomMember bannedByMember =
                new QChatRoomMember("openChatBanActorMember");
        QOpenChatMemberProfile bannedByProfile =
                new QOpenChatMemberProfile("openChatBanActorProfile");

        BooleanBuilder where = new BooleanBuilder()
                .and(ban.chatRoom.id.eq(roomId))
                .and(ban.releasedAt.isNull());

        if (cursorId != null) {
            where.and(ban.id.lt(cursorId));
        }
        if (keyword != null && !keyword.isBlank()) {
            where.and(
                    ban.nicknameSnapshot.containsIgnoreCase(keyword)
                            .or(ban.targetMemberCode.containsIgnoreCase(
                                    keyword
                            ))
            );
        }

        return queryFactory
                .select(Projections.constructor(
                        OpenChatBanQueryRow.class,
                        ban.id,
                        ban.chatRoom.id,
                        ban.targetChatRoomMember.id,
                        ban.targetMemberCode,
                        ban.nicknameSnapshot,
                        ban.profileImageObjectKeySnapshot,
                        ban.lastJoinedAtSnapshot,
                        bannedByMember.id,
                        bannedByProfile.nickname,
                        ban.bannedByRole,
                        ban.bannedAt,
                        ban.reason,
                        ban.targetRoleSnapshot
                ))
                .from(ban)
                .join(ban.bannedByMember, bannedByMember)
                .join(bannedByProfile)
                .on(bannedByProfile.chatRoomMember.eq(bannedByMember))
                .where(where)
                .orderBy(ban.id.desc())
                .limit(limit)
                .fetch();
    }
}
