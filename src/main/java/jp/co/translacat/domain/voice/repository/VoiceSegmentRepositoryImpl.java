package jp.co.translacat.domain.voice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.enums.VoiceChannel;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static jp.co.translacat.domain.voice.entity.QVoiceSegment.voiceSegment;

@Repository
@RequiredArgsConstructor
public class VoiceSegmentRepositoryImpl
        implements VoiceSegmentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<VoiceSegment> findOwnedForUpdate(
            Long segmentId,
            String sessionId,
            Long userId
    ) {
        VoiceSegment result = queryFactory
                .selectFrom(voiceSegment)
                .where(
                        voiceSegment.id.eq(segmentId),
                        voiceSegment.session.id.eq(sessionId),
                        voiceSegment.session.user.id.eq(userId)
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public long findMaxSequence(
            String sessionId,
            VoiceChannel channel
    ) {
        Long maxSequence = queryFactory
                .select(voiceSegment.utteranceSequence.max())
                .from(voiceSegment)
                .where(
                        voiceSegment.session.id.eq(sessionId),
                        voiceSegment.channel.eq(channel)
                )
                .fetchOne();

        return maxSequence == null ? 0L : maxSequence;
    }

    @Override
    public List<VoiceSegment> findPage(
            String sessionId,
            Long cursor,
            int limit
    ) {
        return queryFactory
                .selectFrom(voiceSegment)
                .where(
                        voiceSegment.session.id.eq(sessionId),
                        cursor == null
                                ? null
                                : voiceSegment.id.gt(cursor)
                )
                .orderBy(voiceSegment.id.asc())
                .limit(limit)
                .fetch();
    }
}
