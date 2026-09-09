package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.ai.port.ListeningAiClient;
import jp.co.translacat.domain.languagelearning.listening.daily.validator.ListeningGenerationResponseValidator;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.support.ListeningAiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListeningGenerationWorker {

    private final ListeningGenerationTransactionService transactionService;
    private final ListeningGenerationResponseValidator validator;
    private final ListeningAiClient aiClient;

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
                    work.request().setContext().learningMode(),
                    1.0,
                    work.maxAudioSeconds()
            );
            transactionService.apply(work, response);
        } catch (ListeningAiException exception) {
            log.warn(
                    "Listening generation AI call failed. eventId={}, "
                            + "dailySetId={}, attempt={}, errorCode={}, "
                            + "failedStage={}, retryable={}",
                    event.id(),
                    event.aggregateId(),
                    event.attemptCount(),
                    exception.getErrorCode(),
                    exception.getFailedStage(),
                    exception.isRetryable(),
                    exception
            );
            fail(work, event, exception.getMessage(), exception.isRetryable(),
                    exception.getRetryAfter());
        } catch (RuntimeException exception) {
            log.warn(
                    "Listening generation failed before completion. "
                            + "eventId={}, dailySetId={}, attempt={}",
                    event.id(),
                    event.aggregateId(),
                    event.attemptCount(),
                    exception
            );
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
        transactionService.recordFailure(event, reason, retryable, retryAfter);
    }
}
