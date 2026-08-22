package jp.co.translacat.domain.voice.repository;

import jp.co.translacat.domain.voice.entity.VoiceUsageLedger;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceUsageLedgerRepository
        extends JpaRepository<VoiceUsageLedger, Long>, VoiceUsageLedgerRepositoryCustom {

    boolean existsBySegmentId(Long segmentId);
}
