package jp.co.translacat.domain.languagelearning.level.repository;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LevelTestSessionRepository extends JpaRepository<LevelTestSession, Long> {

    Optional<LevelTestSession> findTopByUserIdAndStatusOrderByStartedAtDesc(
            Long userId,
            LevelTestSessionStatus status
    );

    Optional<LevelTestSession> findTopByUserIdAndSessionTypeAndStatusOrderByCompletedAtDesc(
            Long userId,
            LevelTestSessionType type,
            LevelTestSessionStatus status
    );
}
