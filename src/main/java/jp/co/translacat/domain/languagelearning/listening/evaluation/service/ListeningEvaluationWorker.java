package jp.co.translacat.domain.languagelearning.listening.evaluation.service;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.ai.port.ListeningAiClient;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.evaluation.validator.ListeningEvaluationResponseValidator;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.listening.support.ListeningAiException;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ListeningEvaluationWorker {

    private final ListeningEvaluationTransactionService transactionService;
    private final ListeningEvaluationResponseValidator validator;
    private final ListeningAiClient aiClient;
    private final ListeningAudioStoragePort storagePort;
    private final ListeningOutboxTransactionService outboxTransactionService;
    private final ListeningPolicySettingQueryService policySettingService;

    public void process(ListeningOutboxTransactionService.ClaimedEvent event) {
        ListeningEvaluationTransactionService.EvaluationWork work = null;

        try {
            work = transactionService.prepare(event);
            AiListeningContract.EvaluationResponse response = invoke(work);
            AiListeningContract.TaskResult result = validator.validate(
                    response,
                    work.requestId(),
                    work.profilePolicyVersion(),
                    work.itemId(),
                    work.attemptId(),
                    work.taskType()
            );
            transactionService.apply(work, response, result);
        } catch (ListeningAiException exception) {
            fail(work, event, exception.getErrorCode(), exception.getMessage(),
                    exception.isRetryable(), exception.getRetryAfter());
        } catch (RuntimeException exception) {
            fail(work, event, LanguageLearningErrorCode.AI_EVALUATION_FAILED,
                    exception.getMessage(), false, Duration.ZERO);
        }
    }

    private AiListeningContract.EvaluationResponse invoke(
            ListeningEvaluationTransactionService.EvaluationWork work
    ) {
        return switch (work.taskType()) {
            case DICTATION -> aiClient.evaluateDictation(
                    (AiListeningContract.DictationRequest) work.request()
            );
            case INTERPRETATION -> aiClient.evaluateInterpretation(
                    (AiListeningContract.InterpretationRequest) work.request()
            );
            case REPEAT_AFTER_AUDIO -> {
                var audio = storagePort.load(
                        work.userAudioObjectKey(),
                        work.audioContentType()
                );
                yield aiClient.evaluateRepeat(
                        (AiListeningContract.RepeatRequest) work.request(),
                        audio.bytes(),
                        "listening-repeat",
                        audio.contentType()
                );
            }
        };
    }

    private void fail(
            ListeningEvaluationTransactionService.EvaluationWork work,
            ListeningOutboxTransactionService.ClaimedEvent event,
            String errorCode,
            String reason,
            boolean retryable,
            Duration retryAfter
    ) {
        int limit = policySettingService.get().getAutomaticRetryLimit();
        var failed = outboxTransactionService.fail(
                event.id(),
                reason,
                retryable,
                retryAfter,
                limit,
                LocalDateTime.now()
        );

        if (work != null) {
            transactionService.markFailure(
                    work,
                    errorCode,
                    retryable && !failed.exhausted(),
                    failed.exhausted()
            );
        }
    }
}
