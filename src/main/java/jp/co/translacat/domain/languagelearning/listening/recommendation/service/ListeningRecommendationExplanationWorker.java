package jp.co.translacat.domain.languagelearning.listening.recommendation.service;

import jp.co.translacat.domain.languagelearning.listening.ai.port.ListeningAiClient;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.listening.support.ListeningAiException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ListeningRecommendationExplanationWorker {

    private final ListeningRecommendationExplanationTransactionService
            transactionService;
    private final ListeningAiClient aiClient;
    private final ListeningOutboxTransactionService outboxTransactionService;
    private final ListeningPolicySettingQueryService policySettingService;

    public void process(ListeningOutboxTransactionService.ClaimedEvent event) {
        try {
            var work = transactionService.prepare(event);
            var response = aiClient.explainRecommendation(work.request());
            transactionService.apply(work, response);
        } catch (ListeningAiException exception) {
            fail(event, exception.getMessage(), exception.isRetryable(),
                    exception.getRetryAfter());
        } catch (RuntimeException exception) {
            fail(event, exception.getMessage(), false, Duration.ZERO);
        }
    }

    private void fail(
            ListeningOutboxTransactionService.ClaimedEvent event,
            String reason,
            boolean retryable,
            Duration retryAfter
    ) {
        outboxTransactionService.fail(
                event.id(),
                reason,
                retryable,
                retryAfter,
                policySettingService.get().getAutomaticRetryLimit(),
                LocalDateTime.now()
        );
    }
}
