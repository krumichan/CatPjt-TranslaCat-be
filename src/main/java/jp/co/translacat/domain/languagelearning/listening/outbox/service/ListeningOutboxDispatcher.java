package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningGenerationWorker;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningTtsWorker;
import jp.co.translacat.domain.languagelearning.listening.evaluation.service.ListeningEvaluationWorker;
import jp.co.translacat.domain.languagelearning.listening.profile.service.ListeningProfileRecalculationCommandService;
import jp.co.translacat.domain.languagelearning.listening.recommendation.service.ListeningRecommendationExplanationWorker;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ListeningOutboxDispatcher {

    private final ListeningOutboxTransactionService transactionService;
    private final ListeningGenerationWorker generationWorker;
    private final ListeningTtsWorker ttsWorker;
    private final ListeningEvaluationWorker evaluationWorker;
    private final ListeningProfileRecalculationCommandService profileRecalculationService;
    private final ListeningRecommendationExplanationWorker explanationWorker;

    private final TaskExecutor generationExecutor;
    private final TaskExecutor audioExecutor;
    private final TaskExecutor evaluationExecutor;
    private final ConcurrentHashMap<Long, ListeningOutboxTransactionService.ClaimedEvent>
            locallyClaimedEvents = new ConcurrentHashMap<>();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private LocalDateTime lastLeaseRenewal = LocalDateTime.MIN;

    public ListeningOutboxDispatcher(
            ListeningOutboxTransactionService transactionService,
            ListeningGenerationWorker generationWorker,
            ListeningTtsWorker ttsWorker,
            ListeningEvaluationWorker evaluationWorker,
            ListeningProfileRecalculationCommandService profileRecalculationService,
            ListeningRecommendationExplanationWorker explanationWorker,
            @Qualifier("listeningGenerationExecutor") TaskExecutor generationExecutor,
            @Qualifier("listeningAudioExecutor") TaskExecutor audioExecutor,
            @Qualifier("listeningEvaluationExecutor") TaskExecutor evaluationExecutor
    ) {
        this.transactionService = transactionService;
        this.generationWorker = generationWorker;
        this.ttsWorker = ttsWorker;
        this.evaluationWorker = evaluationWorker;
        this.profileRecalculationService = profileRecalculationService;
        this.explanationWorker = explanationWorker;
        this.generationExecutor = generationExecutor;
        this.audioExecutor = audioExecutor;
        this.evaluationExecutor = evaluationExecutor;
    }

    public void dispatch() {
        if (stopping.get()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(lastLeaseRenewal.plusSeconds(30))) {
            locallyClaimedEvents.values().forEach(event -> transactionService.renewClaim(event, now));
            lastLeaseRenewal = now;
        }
        transactionService.reclaimStale(now, Duration.ofMinutes(5));

        for (var pending : transactionService.pendingEvents(now)) {
            TaskExecutor executor = switch (pending.type()) {
                case GENERATE_SET -> generationExecutor;
                case GENERATE_TTS -> audioExecutor;
                default -> evaluationExecutor;
            };
            try {
                executor.execute(() -> process(pending.id()));
            } catch (TaskRejectedException exception) {
                // Full worker pool: leave the durable event PENDING for the next poll.
            }
        }
    }

    private void process(Long eventId) {
        if (stopping.get()) {
            return;
        }
        transactionService.claim(eventId, LocalDateTime.now()).ifPresent(event -> {
            locallyClaimedEvents.put(event.id(), event);
            try {
                if (stopping.get()) {
                    transactionService.releaseClaimed(event, LocalDateTime.now(), "Listening worker shutdown");
                    return;
                }
                route(event);
            } finally {
                locallyClaimedEvents.remove(event.id(), event);
            }
        });
    }

    @PreDestroy
    public void releaseLocallyClaimedEvents() {
        stopping.set(true);
        if (locallyClaimedEvents.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (var event : List.copyOf(locallyClaimedEvents.values())) {
            try {
                transactionService.releaseClaimed(
                        event,
                        now,
                        "Listening BE 종료/재기동으로 처리 중 작업을 즉시 재개 대기로 전환합니다."
                );
                log.info(
                        "Listening Outbox released on shutdown. eventId={}",
                        event.id()
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Listening Outbox shutdown release failed. eventId={}",
                        event.id(),
                        exception
                );
            }
        }
    }

    private void route(ListeningOutboxTransactionService.ClaimedEvent event) {
        switch (event.type()) {
            case GENERATE_SET -> generationWorker.process(event);
            case GENERATE_TTS -> ttsWorker.process(event);
            case EVALUATE_TASK -> evaluationWorker.process(event);
            case RECALCULATE_PROFILE ->
                    profileRecalculationService.recalculate(event);
            case EXPLAIN_RECOMMENDATION -> explanationWorker.process(event);
            default -> transactionService.fail(
                    event.id(),
                    "지원하지 않는 Listening Outbox Event입니다.",
                    false,
                    Duration.ZERO,
                    0,
                    LocalDateTime.now()
            );
        }
    }
}
