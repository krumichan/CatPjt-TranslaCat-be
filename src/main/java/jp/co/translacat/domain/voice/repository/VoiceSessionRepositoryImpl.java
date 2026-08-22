package jp.co.translacat.domain.voice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.enums.VoiceSessionStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static jp.co.translacat.domain.voice.entity.QVoiceSession.voiceSession;

@Repository
@RequiredArgsConstructor
public class VoiceSessionRepositoryImpl
        implements VoiceSessionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<VoiceSession> findOwnedForUpdate(
            String sessionId,
            Long userId
    ) {
        VoiceSession result = queryFactory
                .selectFrom(voiceSession)
                .where(
                        voiceSession.id.eq(sessionId),
                        voiceSession.user.id.eq(userId)
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<VoiceSession> findActiveByUserId(
            Long userId,
            Collection<VoiceSessionStatus> statuses
    ) {
        return queryFactory
                .selectFrom(voiceSession)
                .where(
                        voiceSession.user.id.eq(userId),
                        voiceSession.status.in(statuses)
                )
                .fetch();
    }

    @Override
    public List<VoiceSession> findHistory(
            Long userId,
            Collection<VoiceSessionStatus> statuses,
            LocalDateTime cursor,
            int limit
    ) {
        return queryFactory
                .selectFrom(voiceSession)
                .where(
                        voiceSession.user.id.eq(userId),
                        voiceSession.saveTranscript.isTrue(),
                        voiceSession.status.in(statuses),
                        cursor == null
                                ? null
                                : voiceSession.createdAt.lt(cursor)
                )
                .orderBy(voiceSession.createdAt.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<VoiceSession> findStale(
            Collection<VoiceSessionStatus> statuses,
            LocalDateTime threshold,
            int limit
    ) {
        return queryFactory
                .selectFrom(voiceSession)
                .where(
                        voiceSession.status.in(statuses),
                        voiceSession.updatedAt.lt(threshold)
                )
                .orderBy(voiceSession.updatedAt.asc())
                .limit(limit)
                .fetch();
    }
}
