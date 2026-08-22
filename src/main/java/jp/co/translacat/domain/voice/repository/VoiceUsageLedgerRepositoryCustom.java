package jp.co.translacat.domain.voice.repository;

import java.time.LocalDate;

public interface VoiceUsageLedgerRepositoryCustom {

    long sumProcessedAudioMs(
            Long userId,
            LocalDate usageDate
    );
}
