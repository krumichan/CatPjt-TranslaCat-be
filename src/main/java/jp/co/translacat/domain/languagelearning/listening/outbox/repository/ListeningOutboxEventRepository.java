package jp.co.translacat.domain.languagelearning.listening.outbox.repository;

import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.outbox.entity.ListeningOutboxEvent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ListeningOutboxEventRepository
        extends JpaRepository<ListeningOutboxEvent, Long> {

    List<ListeningOutboxEvent>
    findTop50ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            ListeningOutboxStatus status,
            LocalDateTime now
    );

    List<ListeningOutboxEvent>
    findTop50ByStatusAndEventTypeAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            ListeningOutboxStatus status,
            ListeningOutboxType eventType,
            LocalDateTime now
    );

    List<ListeningOutboxEvent>
    findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            ListeningOutboxStatus status,
            LocalDateTime updatedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ListeningOutboxEvent>
    findTop50LockedByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            ListeningOutboxStatus status,
            LocalDateTime updatedAt
    );

    Optional<ListeningOutboxEvent> findByIdempotencyKey(String idempotencyKey);

    boolean existsByEventTypeAndAggregateIdAndStatusIn(
            ListeningOutboxType eventType,
            Long aggregateId,
            List<ListeningOutboxStatus> statuses
    );

    Optional<ListeningOutboxEvent>
    findFirstByEventTypeAndAggregateIdOrderByIdDesc(
            ListeningOutboxType eventType,
            Long aggregateId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ListeningOutboxEvent> findLockedById(Long id);
}
