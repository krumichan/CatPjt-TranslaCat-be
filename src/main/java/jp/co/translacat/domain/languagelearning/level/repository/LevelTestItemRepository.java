package jp.co.translacat.domain.languagelearning.level.repository;

import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LevelTestItemRepository extends JpaRepository<LevelTestItem, Long> {
    List<LevelTestItem> findAllBySessionIdOrderByQuestionNumberAsc(Long sessionId);
    Optional<LevelTestItem> findBySessionIdAndQuestionNumber(Long sessionId, int questionNumber);
}
