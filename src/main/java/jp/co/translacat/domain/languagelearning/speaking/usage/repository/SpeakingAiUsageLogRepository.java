package jp.co.translacat.domain.languagelearning.speaking.usage.repository;

import jp.co.translacat.domain.languagelearning.speaking.usage.entity.SpeakingAiUsageLog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpeakingAiUsageLogRepository
        extends JpaRepository<SpeakingAiUsageLog, Long> {

    List<SpeakingAiUsageLog> findAllBySessionIdOrderByCreatedAtAsc(
            Long sessionId
    );
}
