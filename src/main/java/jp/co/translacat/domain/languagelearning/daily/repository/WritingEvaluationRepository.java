package jp.co.translacat.domain.languagelearning.daily.repository;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WritingEvaluationRepository extends JpaRepository<WritingEvaluation, Long> {

    Optional<WritingEvaluation> findByAnswerId(Long answerId);

    Optional<WritingEvaluation> findByLevelTestItemId(Long itemId);

    List<WritingEvaluation> findTop20ByUserIdAndContextAndStatusOrderByEvaluatedAtDesc(
            Long userId,
            WritingEvaluationContext context,
            EvaluationStatus status
    );

    List<WritingEvaluation> findAllByUserIdAndContextAndStatusOrderByEvaluatedAtDesc(
            Long userId,
            WritingEvaluationContext context,
            EvaluationStatus status
    );
}
