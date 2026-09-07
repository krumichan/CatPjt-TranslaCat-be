package jp.co.translacat.domain.languagelearning.speaking.usage.repository;

import jp.co.translacat.domain.languagelearning.speaking.usage.entity.SpeakingAiUsageLog;

import org.springframework.data.jpa.repository.JpaRepository;


public interface SpeakingAiUsageLogRepository
        extends JpaRepository<SpeakingAiUsageLog, Long> {

}
