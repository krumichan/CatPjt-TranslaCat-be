package jp.co.translacat.domain.voice.repository;

import jp.co.translacat.domain.voice.entity.VoiceHistoryGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoiceHistoryGroupRepository extends JpaRepository<VoiceHistoryGroup, String> {
}
