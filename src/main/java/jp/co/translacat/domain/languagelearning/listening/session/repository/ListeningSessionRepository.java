package jp.co.translacat.domain.languagelearning.listening.session.repository;

import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListeningSessionRepository
        extends JpaRepository<ListeningSession, Long> {

    Optional<ListeningSession> findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ListeningSession>
    findFirstByUserIdAndStatusInOrderByStartedAtDesc(
            Long userId,
            Collection<ListeningSessionStatus> statuses
    );

    Optional<ListeningSession> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ListeningSession> findLockedById(Long id);

    Optional<ListeningSession> findFirstByDailySetIdOrderByStartedAtDesc(Long dailySetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ListeningSession> findOwnedLockedByIdAndUserId(
            Long sessionId,
            Long userId
    );

    List<ListeningSession>
    findTop100ByStatusAndLastActivityAtBeforeOrderByLastActivityAtAsc(
            ListeningSessionStatus status,
            LocalDateTime cutoff
    );

    List<ListeningSession>
    findAllByUserIdAndDailySetLearningDateBetweenOrderByStartedAtDesc(
            Long userId,
            LocalDate from,
            LocalDate to
    );
}
