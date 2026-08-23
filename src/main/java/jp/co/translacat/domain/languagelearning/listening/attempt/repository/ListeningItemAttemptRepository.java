package jp.co.translacat.domain.languagelearning.listening.attempt.repository;

import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvaluationPurpose;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface ListeningItemAttemptRepository
        extends JpaRepository<ListeningItemAttempt, Long> {

    Optional<ListeningItemAttempt>
    findBySessionIdAndItemIdAndEvaluationPurpose(
            Long sessionId,
            Long itemId,
            ListeningEvaluationPurpose purpose
    );

    Optional<ListeningItemAttempt> findBySessionIdAndIdempotencyKey(
            Long sessionId,
            String idempotencyKey
    );

    long countBySessionIdAndItemIdAndEvaluationPurpose(
            Long sessionId,
            Long itemId,
            ListeningEvaluationPurpose purpose
    );

    boolean existsByItemIdAndEvaluationPurpose(
            Long itemId,
            ListeningEvaluationPurpose purpose
    );

    List<ListeningItemAttempt>
    findAllBySessionIdOrderByItemItemIndexAscAttemptNoAsc(Long sessionId);

    Optional<ListeningItemAttempt> findByIdAndSessionUserId(
            Long attemptId,
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ListeningItemAttempt> findLockedById(Long attemptId);
}
