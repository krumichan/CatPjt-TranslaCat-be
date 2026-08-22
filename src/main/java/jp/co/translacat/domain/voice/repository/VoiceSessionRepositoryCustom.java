package jp.co.translacat.domain.voice.repository;

import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.enums.VoiceSessionStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VoiceSessionRepositoryCustom {

    Optional<VoiceSession> findOwnedForUpdate(
            String sessionId,
            Long userId
    );

    List<VoiceSession> findActiveByUserId(
            Long userId,
            Collection<VoiceSessionStatus> statuses
    );

    List<VoiceSession> findHistory(
            Long userId,
            Collection<VoiceSessionStatus> statuses,
            LocalDateTime cursor,
            int limit
    );

    List<VoiceSession> findStale(
            Collection<VoiceSessionStatus> statuses,
            LocalDateTime threshold,
            int limit
    );
}
