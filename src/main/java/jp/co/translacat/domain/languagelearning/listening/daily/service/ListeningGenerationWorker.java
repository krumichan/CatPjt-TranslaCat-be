package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.ai.port.ListeningAiClient;
import jp.co.translacat.domain.languagelearning.listening.daily.validator.ListeningGenerationResponseValidator;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.listening.support.ListeningAiException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ListeningGenerationWorker {

    private final ListeningGenerationTransactionService transactionService;
    private final ListeningGenerationResponseValidator validator;
    private final ListeningAiClient aiClient;
    private final ListeningOutboxTransactionService outboxTransactionService;
    private final ListeningPolicySettingQueryService policySettingService;

    public void process(ListeningOutboxTransactionService.ClaimedEvent event) {
        ListeningGenerationTransactionService.GenerationWork work = null;

        try {
            work = transactionService.prepare(event);
            AiListeningContract.GenerationResponse response =
                    aiClient.generateSet(work.request());
            validator.validate(
                    response,
                    work.request().requestId(),
                    work.request().policyVersion(),
                    work.request().modelConfigVersion(),
                    work.expectedCount(),
                    1.0,
                    work.maxAudioSeconds()
            );
            transactionService.apply(work, response);
        } catch (ListeningAiException exception) {
            fail(work, event, exception.getMessage(), exception.isRetryable(),
                    exception.getRetryAfter());
        } catch (RuntimeException exception) {
            fail(work, event, exception.getMessage(), false, Duration.ZERO);
        }
    }

    private void fail(
            ListeningGenerationTransactionService.GenerationWork work,
            ListeningOutboxTransactionService.ClaimedEvent event,
            String reason,
            boolean retryable,
            Duration retryAfter
    ) {
        int limit = policySettingService.get().getAutomaticRetryLimit();
        var result = outboxTransactionService.fail(
                event.id(),
                reason,
                retryable,
                retryAfter,
                limit,
                LocalDateTime.now()
        );

        if (result.exhausted() && work != null) {
            transactionService.failPermanently(work, reason);
        }
    }
}
