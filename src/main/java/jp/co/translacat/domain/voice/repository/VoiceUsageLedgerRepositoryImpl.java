package jp.co.translacat.domain.voice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;

import static jp.co.translacat.domain.voice.entity.QVoiceUsageLedger.voiceUsageLedger;

@Repository
@RequiredArgsConstructor
public class VoiceUsageLedgerRepositoryImpl
        implements VoiceUsageLedgerRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public long sumProcessedAudioMs(
            Long userId,
            LocalDate usageDate
    ) {
        Long total = queryFactory
                .select(voiceUsageLedger.processedAudioMs.sum())
                .from(voiceUsageLedger)
                .where(
                        voiceUsageLedger.user.id.eq(userId),
                        voiceUsageLedger.usageDate.eq(usageDate)
                )
                .fetchOne();

        return total == null ? 0L : total;
    }
}
