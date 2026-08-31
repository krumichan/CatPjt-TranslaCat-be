package jp.co.translacat.domain.languagelearning.level.repository;

import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAssessmentVersion;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LevelTestSessionRepository
        extends JpaRepository<LevelTestSession, Long> {

    Optional<LevelTestSession> findTopByUserIdAndStatusOrderByStartedAtDesc(
            Long userId,
            LevelTestSessionStatus status
    );

    Optional<LevelTestSession> findTopByUserIdAndAssessmentVersionAndStatusOrderByStartedAtDesc(
            Long userId,
            LevelTestAssessmentVersion assessmentVersion,
            LevelTestSessionStatus status
    );

    Optional<LevelTestSession> findTopByUserIdAndSessionTypeAndStatusOrderByCompletedAtDesc(
            Long userId,
            LevelTestSessionType type,
            LevelTestSessionStatus status
    );

    Optional<LevelTestSession> findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );

    Optional<LevelTestSession> findByIdAndUserId(
            Long id,
            Long userId
    );

    boolean existsByUserIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Long userId,
            LevelTestSessionStatus status,
            LocalDateTime from,
            LocalDateTime to
    );

    List<LevelTestSession> findAllByUserIdAndStatusOrderByCompletedAtDesc(
            Long userId,
            LevelTestSessionStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LevelTestSession> findLockedById(Long id);
}
