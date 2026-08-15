package jp.co.translacat.domain.languagelearning.speaking.turn.repository;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTurnStatus;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpeakingTurnRepository
        extends JpaRepository<SpeakingTurn, Long> {

    Optional<SpeakingTurn> findByIdAndSessionIdAndSessionUserId(
            Long id,
            Long sessionId,
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SpeakingTurn> findOneByIdAndSessionIdAndSessionUserId(
            Long id,
            Long sessionId,
            Long userId
    );

    Optional<SpeakingTurn> findBySessionIdAndTurnIndex(
            Long sessionId,
            int turnIndex
    );

    Optional<SpeakingTurn> findBySessionIdAndIdempotencyKey(
            Long sessionId,
            String idempotencyKey
    );

    List<SpeakingTurn> findAllBySessionIdOrderByTurnIndexAsc(Long sessionId);

    long countBySessionIdAndStatusIn(
            Long sessionId,
            List<SpeakingTurnStatus> statuses
    );

    List<SpeakingTurn> findAllByUserAudioRetentionUntilBeforeAndUserAudioObjectKeyIsNotNull(
            LocalDateTime before
    );

    List<SpeakingTurn> findAllByAssistantAudioRetentionUntilBeforeAndAssistantAudioObjectKeyIsNotNull(
            LocalDateTime before
    );
}
