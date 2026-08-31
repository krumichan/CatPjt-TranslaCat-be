package jp.co.translacat.domain.languagelearning.listening.playback.repository;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningPlaybackType;
import jp.co.translacat.domain.languagelearning.listening.playback.entity.ListeningPlaybackEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ListeningPlaybackEventRepository
        extends JpaRepository<ListeningPlaybackEvent, Long> {

    boolean existsByAttemptIdAndClientEventId(
            Long attemptId,
            String clientEventId
    );

    long countByAttemptIdAndPlaybackTypeAndOccurredAtLessThanEqual(
            Long attemptId,
            ListeningPlaybackType playbackType,
            LocalDateTime submittedAt
    );
}
