package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.repository;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.entity.SpeakingReadAloudProblemEvaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpeakingReadAloudProblemEvaluationRepository
        extends JpaRepository<SpeakingReadAloudProblemEvaluation, Long> {

    Optional<SpeakingReadAloudProblemEvaluation> findBySessionIdAndProblemIndex(
            Long sessionId,
            int problemIndex
    );

    List<SpeakingReadAloudProblemEvaluation> findAllBySessionIdOrderByProblemIndexAsc(
            Long sessionId
    );
}
