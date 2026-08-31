package jp.co.translacat.domain.languagelearning.level.repository;

import jp.co.translacat.domain.languagelearning.level.entity.LevelTestEvaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LevelTestEvaluationRepository
        extends JpaRepository<LevelTestEvaluation, Long> {

    Optional<LevelTestEvaluation> findByResponseId(Long responseId);

    List<LevelTestEvaluation> findAllByResponseItemSessionIdOrderByResponseItemQuestionNumberAsc(
            Long sessionId
    );
}
