package jp.co.translacat.domain.languagelearning.listening.evaluation.repository;

import jp.co.translacat.domain.languagelearning.listening.evaluation.entity.ListeningTaskEvaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ListeningTaskEvaluationRepository
        extends JpaRepository<ListeningTaskEvaluation, Long>,
        ListeningTaskEvaluationRepositoryCustom {

    Optional<ListeningTaskEvaluation>
    findByTaskResponseIdAndEvaluationVersion(
            Long taskResponseId,
            String evaluationVersion
    );

    Optional<ListeningTaskEvaluation>
    findFirstByTaskResponseIdOrderByEvaluatedAtDesc(Long taskResponseId);

    List<ListeningTaskEvaluation>
    findAllByTaskResponseAttemptIdOrderByTaskTypeAsc(Long attemptId);
}
