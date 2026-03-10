package jp.co.translacat.domain.voice.repository;

import jp.co.translacat.domain.voice.entity.VoiceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoiceHistoryRepository extends JpaRepository<VoiceHistory, Long> {
}
