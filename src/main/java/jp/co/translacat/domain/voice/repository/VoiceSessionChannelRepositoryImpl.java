package jp.co.translacat.domain.voice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.voice.entity.VoiceSessionChannel;
import jp.co.translacat.domain.voice.enums.VoiceChannel;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.Optional;

import static jp.co.translacat.domain.voice.entity.QVoiceSessionChannel.voiceSessionChannel;

@Repository
@RequiredArgsConstructor
public class VoiceSessionChannelRepositoryImpl
        implements VoiceSessionChannelRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<VoiceSessionChannel> findOwnedForUpdate(
            String sessionId,
            VoiceChannel channel,
            Long userId
    ) {
        VoiceSessionChannel result = queryFactory
                .selectFrom(voiceSessionChannel)
                .where(
                        voiceSessionChannel.session.id.eq(sessionId),
                        voiceSessionChannel.channel.eq(channel),
                        voiceSessionChannel.session.user.id.eq(userId)
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(result);
    }
}
