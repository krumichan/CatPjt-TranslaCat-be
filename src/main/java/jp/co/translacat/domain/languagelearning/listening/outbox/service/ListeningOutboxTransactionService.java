package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.outbox.entity.ListeningOutboxEvent;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ListeningOutboxTransactionService {

    private final ListeningOutboxEventRepository repository;

    @Transactional
    public void reclaimStale(LocalDateTime now, Duration lease) {
        LocalDateTime cutoff = now.minus(
                lease == null ? Duration.ofMinutes(5) : lease
        );
        repository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ListeningOutboxStatus.PROCESSING,
                cutoff
        ).forEach(value -> value.reclaim(now));
    }

    @Transactional(readOnly = true)
    public List<Long> pendingIds(LocalDateTime now) {
        return repository
                .findTop50ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                        ListeningOutboxStatus.PENDING,
                        now
                ).stream()
                .map(ListeningOutboxEvent::getId)
                .toList();
    }

    @Transactional
    public Optional<ClaimedEvent> claim(Long eventId, LocalDateTime now) {
        return repository.findLockedById(eventId)
                .filter(value -> value.getStatus() == ListeningOutboxStatus.PENDING)
                .filter(value -> !value.getAvailableAt().isAfter(now))
                .map(value -> {
                    value.claim();

                    return new ClaimedEvent(
                            value.getId(),
                            value.getEventType(),
                            value.getAggregateId(),
                            value.getPayloadJson(),
                            value.getIdempotencyKey(),
                            value.getAttemptCount()
                    );
                });
    }

    @Transactional
    public void succeed(Long eventId, LocalDateTime now) {
        repository.findLockedById(eventId).ifPresent(value -> value.succeed(now));
    }

    @Transactional
    public FailureResult fail(
            Long eventId,
            String reason,
            boolean retryable,
            Duration retryAfter,
            int automaticRetryLimit,
            LocalDateTime now
    ) {
        ListeningOutboxEvent event = repository.findLockedById(eventId)
                .orElseThrow();
        event.fail(
                reason,
                retryable,
                retryAfter,
                automaticRetryLimit,
                now
        );

        return new FailureResult(
                event.getStatus() == ListeningOutboxStatus.FAILED,
                event.getAttemptCount()
        );
    }

    public record ClaimedEvent(
            Long id,
            ListeningOutboxType type,
            Long aggregateId,
            String payloadJson,
            String idempotencyKey,
            int attemptCount
    ) {
    }

    public record FailureResult(boolean exhausted, int attemptCount) {
    }
}
