package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningGenerationWorker;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningTtsWorker;
import jp.co.translacat.domain.languagelearning.listening.evaluation.service.ListeningEvaluationWorker;
import jp.co.translacat.domain.languagelearning.listening.profile.service.ListeningProfileRecalculationCommandService;
import jp.co.translacat.domain.languagelearning.listening.recommendation.service.ListeningRecommendationExplanationWorker;

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListeningOutboxDispatcher {

    private final ListeningOutboxTransactionService transactionService;
    private final ListeningGenerationWorker generationWorker;
    private final ListeningTtsWorker ttsWorker;
    private final ListeningEvaluationWorker evaluationWorker;
    private final ListeningProfileRecalculationCommandService profileRecalculationService;
    private final ListeningRecommendationExplanationWorker explanationWorker;

    private final Set<Long> locallyClaimedEventIds = ConcurrentHashMap.newKeySet();

    public void dispatch() {
        LocalDateTime now = LocalDateTime.now();
        transactionService.reclaimStale(now, Duration.ofMinutes(5));

        for (Long eventId : transactionService.pendingIds(now)) {
            transactionService.claim(eventId, now).ifPresent(event -> {
                locallyClaimedEventIds.add(event.id());
                try {
                    route(event);
                } finally {
                    locallyClaimedEventIds.remove(event.id());
                }
            });
        }
    }

    @PreDestroy
    public void releaseLocallyClaimedEvents() {
        if (locallyClaimedEventIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long eventId : List.copyOf(locallyClaimedEventIds)) {
            try {
                transactionService.releaseClaimed(
                        eventId,
                        now,
                        "Listening BE 종료/재기동으로 처리 중 작업을 즉시 재개 대기로 전환합니다."
                );
                log.info(
                        "Listening Outbox released on shutdown. eventId={}",
                        eventId
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Listening Outbox shutdown release failed. eventId={}",
                        eventId,
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
