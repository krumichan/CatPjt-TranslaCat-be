package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningGenerationWorker;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningTtsWorker;
import jp.co.translacat.domain.languagelearning.listening.evaluation.service.ListeningEvaluationWorker;
import jp.co.translacat.domain.languagelearning.listening.profile.service.ListeningProfileRecalculationCommandService;
import jp.co.translacat.domain.languagelearning.listening.recommendation.service.ListeningRecommendationExplanationWorker;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ListeningOutboxDispatcher {

    private final ListeningOutboxTransactionService transactionService;
    private final ListeningGenerationWorker generationWorker;
    private final ListeningTtsWorker ttsWorker;
    private final ListeningEvaluationWorker evaluationWorker;
    private final ListeningProfileRecalculationCommandService profileRecalculationService;
    private final ListeningRecommendationExplanationWorker explanationWorker;

    @Scheduled(
            fixedDelayString = "${language-learning.listening.outbox-delay-ms:1000}"
    )
    public void dispatch() {
        LocalDateTime now = LocalDateTime.now();
        transactionService.reclaimStale(now, Duration.ofMinutes(5));

        for (Long eventId : transactionService.pendingIds(now)) {
            transactionService.claim(eventId, now).ifPresent(this::route);
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
