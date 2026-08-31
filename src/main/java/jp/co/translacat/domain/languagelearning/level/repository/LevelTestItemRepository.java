package jp.co.translacat.domain.languagelearning.level.repository;

import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface LevelTestItemRepository
        extends JpaRepository<LevelTestItem, Long> {

    List<LevelTestItem> findAllBySessionIdOrderByQuestionNumberAsc(
            Long sessionId
    );

    Optional<LevelTestItem> findBySessionIdAndQuestionNumber(
            Long sessionId,
            int questionNumber
    );

    Optional<LevelTestItem> findByIdAndSessionUserId(
            Long itemId,
            Long userId
    );

    long countBySessionId(Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LevelTestItem> findLockedById(Long itemId);
}
