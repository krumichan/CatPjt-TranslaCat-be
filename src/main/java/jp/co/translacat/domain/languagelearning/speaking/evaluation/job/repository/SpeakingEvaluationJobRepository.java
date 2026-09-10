package jp.co.translacat.domain.languagelearning.speaking.evaluation.job.repository;

import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.entity.SpeakingEvaluationJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpeakingEvaluationJobRepository extends JpaRepository<SpeakingEvaluationJob, Long> {
    Optional<SpeakingEvaluationJob> findBySessionIdAndProblemIndex(Long sessionId, int problemIndex);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SpeakingEvaluationJob> findOneByIdAndSessionId(Long id, Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SpeakingEvaluationJob> findOneBySessionIdAndProblemIndex(Long sessionId, int problemIndex);

    List<SpeakingEvaluationJob> findAllByStatusInAndAvailableAtLessThanEqualOrderByAvailableAtAscIdAsc(
            Collection<SpeakingEvaluationJob.Status> statuses, LocalDateTime now, Pageable pageable);
}
