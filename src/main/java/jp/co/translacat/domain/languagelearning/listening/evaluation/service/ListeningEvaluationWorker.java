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
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
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

        log.info(
                "Listening evaluation worker started. eventId={} responseId={} attemptCount={}",
                event.id(), event.aggregateId(), event.attemptCount()
        );

        try {
            work = transactionService.prepare(event);
            log.info(
                    "Listening evaluation request prepared. eventId={} responseId={} itemId={} "
                            + "attemptId={} taskType={} requestId={}",
                    event.id(), work.responseId(), work.itemId(), work.attemptId(),
                    work.taskType(), work.requestId()
            );
            AiListeningContract.EvaluationResponse response = invoke(work);
            log.info(
                    "Listening evaluation AI call completed. eventId={} responseId={} itemId={} "
                            + "taskType={} responseRequestId={}",
                    event.id(), work.responseId(), work.itemId(), work.taskType(),
                    response == null ? null : response.requestId()
            );
            AiListeningContract.TaskResult result = validator.validate(
                    response,
                    work.requestId(),
                    work.profilePolicyVersion(),
                    work.itemId(),
                    work.attemptId(),
                    work.taskType()
            );
            transactionService.apply(work, response, result);
            log.info(
                    "Listening evaluation worker completed. eventId={} responseId={} itemId={} "
                            + "taskType={} score={} evaluable={}",
                    event.id(), work.responseId(), work.itemId(), work.taskType(),
                    result.score(), result.evaluable()
            );
        } catch (ListeningAiException exception) {
            log.warn(
                    "Listening evaluation AI call failed. eventId={} responseId={} itemId={} "
                            + "taskType={} code={} stage={} retryable={} message={}",
                    event.id(),
                    work == null ? event.aggregateId() : work.responseId(),
                    work == null ? null : work.itemId(),
                    work == null ? null : work.taskType(),
                    exception.getErrorCode(),
                    exception.getFailedStage(),
                    exception.isRetryable(),
                    exception.getMessage()
            );
            fail(work, event, exception.getErrorCode(), exception.getMessage(),
                    exception.isRetryable(), exception.getRetryAfter());
        } catch (RuntimeException exception) {
            log.error(
                    "Listening evaluation worker failed unexpectedly. eventId={} responseId={} "
                            + "itemId={} taskType={}",
                    event.id(),
                    work == null ? event.aggregateId() : work.responseId(),
                    work == null ? null : work.itemId(),
                    work == null ? null : work.taskType(),
                    exception
            );
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
            case COMPREHENSION -> aiClient.evaluateComprehension(
                    (AiListeningContract.ComprehensionRequest) work.request()
            );
            case SUMMARY -> aiClient.evaluateSummary(
                    (AiListeningContract.SummaryRequest) work.request()
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
