package jp.co.translacat.domain.languagelearning.listening.evaluation.repository;

import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.listening.evaluation.entity.ListeningTaskEvaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ListeningTaskEvaluation>
    findAllLockedByTaskResponseAttemptIdOrderByTaskTypeAsc(Long attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ListeningTaskEvaluation> findLockedByTaskResponseIdAndEvaluationVersion(
            Long taskResponseId, String evaluationVersion
    );
}
