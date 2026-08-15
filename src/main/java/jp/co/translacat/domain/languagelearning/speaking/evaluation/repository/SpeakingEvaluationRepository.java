package jp.co.translacat.domain.languagelearning.speaking.evaluation.repository;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpeakingEvaluationRepository
        extends JpaRepository<SpeakingEvaluation, Long> {

    Optional<SpeakingEvaluation> findFirstBySessionIdOrderByEvaluatedAtDesc(
            Long sessionId
    );

    Optional<SpeakingEvaluation> findBySessionIdAndEvaluationVersion(
            Long sessionId,
            String evaluationVersion
    );

    List<SpeakingEvaluation> findAllBySessionUserIdOrderByEvaluatedAtDesc(
            Long userId
    );
}
