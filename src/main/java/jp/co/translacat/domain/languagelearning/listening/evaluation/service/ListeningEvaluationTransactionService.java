package jp.co.translacat.domain.languagelearning.listening.evaluation.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.evaluation.entity.ListeningTaskEvaluation;
import jp.co.translacat.domain.languagelearning.listening.evaluation.repository.ListeningTaskEvaluationRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningEvaluationContractPolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningProfilePolicy;
import jp.co.translacat.domain.languagelearning.listening.profile.entity.ListeningMetricHistory;
import jp.co.translacat.domain.languagelearning.listening.profile.repository.ListeningMetricHistoryRepository;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionLockService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ListeningEvaluationTransactionService {

    private final ListeningTaskResponseRepository responseRepository;
    private final ListeningTaskEvaluationRepository evaluationRepository;
    private final ListeningMetricHistoryRepository historyRepository;
    private final ListeningItemAttemptRepository attemptRepository;
    private final ListeningPolicySettingQueryService policySettingService;
    private final ListeningEvaluationContractPolicy contractPolicy;
    private final ListeningProfilePolicy profilePolicy;
    private final ListeningAttemptFinalizationCommandService finalizationService;
    private final ListeningOutboxTransactionService outboxTransactionService;
    private final LanguageLearningJsonCodec jsonCodec;
    private final ListeningSessionLockService lockService;

    @Transactional(readOnly = true)
    public EvaluationWork prepare(
            ListeningOutboxTransactionService.ClaimedEvent event
    ) {
        ListeningTaskResponse response = responseRepository
                .findById(event.aggregateId())
                .orElseThrow();
        ListeningItemAttempt attempt = response.getAttempt();
        var item = attempt.getItem();
        var set = item.getDailySet();
        ListeningPolicySetting policy = policySettingService.get();
        List<AiListeningContract.AssistanceUsage> assistance = jsonCodec.read(
                response.getAssistanceUsageJson(),
                new TypeReference<List<AiListeningContract.AssistanceUsage>>() {
                }
        );
        String requestId = "be-listening-evaluation-" + event.id();
        AiListeningContract.GeneratedItem generated = jsonCodec.read(
                item.getGenerationMetadataJson(),
                AiListeningContract.GeneratedItem.class
        );
        Object request = switch (response.getTaskType()) {
            case DICTATION -> new AiListeningContract.DictationRequest(
                    requestId,
                    event.idempotencyKey(),
                    item.getId(),
                    attempt.getId(),
                    attempt.getEvaluationPurpose(),
                    attempt.isAnswerRevealed(),
                    assistance,
                    policy.getProfilePolicyVersion(),
                    policy.getModelConfigVersion(),
                    response.getManualRetryCount(),
                    item.getSourceText(),
                    response.getAnswerText(),
                    set.getLearningLanguage(),
                    Map.of()
            );
            case INTERPRETATION ->
                    new AiListeningContract.InterpretationRequest(
                            requestId,
                            event.idempotencyKey(),
                            item.getId(),
                            attempt.getId(),
                            attempt.getEvaluationPurpose(),
                            attempt.isAnswerRevealed(),
                            assistance,
                            policy.getProfilePolicyVersion(),
                            policy.getModelConfigVersion(),
                            response.getManualRetryCount(),
                            item.getSourceText(),
                            jsonCodec.read(
                                    item.getReferenceMeaningsJson(),
                                    new TypeReference<List<String>>() {
                                    }
                            ),
                            jsonCodec.read(
                                    item.getKeyMeaningUnitsJson(),
                                    new TypeReference<List<String>>() {
                                    }
                            ),
                            response.getAnswerText(),
                            set.getOriginLanguage(),
                            set.getLearningLanguage()
                    );
            case COMPREHENSION -> new AiListeningContract.ComprehensionRequest(
                    requestId,
                    event.idempotencyKey(),
                    item.getId(),
                    attempt.getId(),
                    attempt.getEvaluationPurpose(),
                    attempt.isAnswerRevealed(),
                    assistance,
                    policy.getProfilePolicyVersion(),
                    policy.getModelConfigVersion(),
                    response.getManualRetryCount(),
                    generated.question(),
                    generated.options(),
                    response.getAnswerText(),
                    generated.correctOptionKey(),
                    generated.comprehensionFocus(),
                    set.getOriginLanguage(),
                    set.getLearningLanguage()
            );
            case SUMMARY -> new AiListeningContract.SummaryRequest(
                    requestId,
                    event.idempotencyKey(),
                    item.getId(),
                    attempt.getId(),
                    attempt.getEvaluationPurpose(),
                    attempt.isAnswerRevealed(),
                    assistance,
                    policy.getProfilePolicyVersion(),
                    policy.getModelConfigVersion(),
                    response.getManualRetryCount(),
                    item.getSourceText(),
                    generated.summaryKeyPoints(),
                    response.getAnswerText(),
                    set.getOriginLanguage(),
                    set.getLearningLanguage()
            );
            case REPEAT_AFTER_AUDIO -> new AiListeningContract.RepeatRequest(
                    requestId,
                    event.idempotencyKey(),
                    item.getId(),
                    attempt.getId(),
                    attempt.getEvaluationPurpose(),
                    attempt.isAnswerRevealed(),
                    assistance,
                    policy.getProfilePolicyVersion(),
                    policy.getModelConfigVersion(),
                    response.getManualRetryCount(),
                    item.getSourceText(),
                    item.getAudioDurationMs() == null
                            ? item.getEstimatedAudioSeconds()
                            : item.getAudioDurationMs() / 1000.0,
                    set.getLearningLanguage(),
                    jsonCodec.read(
                            item.getKeyMeaningUnitsJson(),
                            new TypeReference<List<String>>() {
                            }
                    )
            );
        };

        return new EvaluationWork(
                event,
                response.getId(),
                attempt.getId(),
                item.getId(),
                response.getTaskType(),
                requestId,
                policy.getProfilePolicyVersion(),
                request,
                response.getUserAudioObjectKey(),
                response.getAudioContentType()
        );
    }

    @Transactional
    public void apply(
            EvaluationWork work,
            AiListeningContract.EvaluationResponse provider,
            AiListeningContract.TaskResult result
    ) {
        lockService.attempt(work.attemptId());
        ListeningTaskResponse response = responseRepository.findLockedById(
                work.responseId()
        ).orElseThrow();
        var existing = evaluationRepository
                .findLockedByTaskResponseIdAndEvaluationVersion(
                        response.getId(),
                        provider.evaluationVersion()
                );

        if (existing.isEmpty()) {
            ListeningTaskEvaluation evaluation = evaluationRepository.save(
                    ListeningTaskEvaluation.create(
                            response,
                            response.getTaskType(),
                            result.evaluable() ? result.score() : null,
                            result.confidence(),
                            result.evaluable(),
                            jsonCodec.write(result.metrics()),
                            jsonCodec.write(Map.of(
                                    "status", result.status(),
                                    "alignment", safe(result.alignment())
                            )),
                            jsonCodec.write(safe(result.strengths())),
                            jsonCodec.write(safe(result.improvements())),
                            jsonCodec.write(safe(result.evidence())),
                            jsonCodec.write(safe(
                                    result.recommendedInterpretations()
                            )),
                            jsonCodec.write(safe(result.profileSignals())),
                            jsonCodec.write(provider),
                            provider.evaluationVersion(),
                            provider.profilePolicyVersion(),
                            result.reasonCode(),
                            LocalDateTime.now()
                    )
            );
            persistProfileSignals(response, evaluation, result, provider);
        }

        if (result.evaluable()) {
            response.markEvaluated();
        } else {
            response.markNotEvaluable();
        }

        outboxTransactionService.succeed(
                work.event().id(),
                LocalDateTime.now()
        );
        finalizationService.finalizeIfTerminal(response.getAttempt().getId());
    }

    @Transactional
    public void markFailure(
            EvaluationWork work,
            String errorCode,
            boolean automaticRetry,
            boolean exhausted
    ) {
        lockService.attempt(work.attemptId());
        ListeningTaskResponse response = responseRepository.findLockedById(
                work.responseId()
        ).orElseThrow();
        ListeningItemAttempt attempt = response.getAttempt();
        response.markEvaluationFailed(
                errorCode,
                automaticRetry,
                exhausted
        );
        attempt.markEvaluationError(errorCode);
        int manualLimit = policySettingService.get()
                .getManualRetryLimit();

        if (exhausted) {
            if (response.getManualRetryCount() >= manualLimit) {
                response.markNotEvaluable();
                String version = "system-not-evaluable-" + response.getId()
                        + "-" + response.getManualRetryCount();

                if (evaluationRepository
                        .findLockedByTaskResponseIdAndEvaluationVersion(
                                response.getId(),
                                version
                        ).isEmpty()) {
                    evaluationRepository.save(ListeningTaskEvaluation.create(
                            response,
                            response.getTaskType(),
                            null,
                            null,
                            false,
                            "[]",
                            "{}",
                            "[]",
                            "[]",
                            "[]",
                            "[]",
                            "[]",
                            "{}",
                            version,
                            ListeningProfilePolicy.VERSION,
                            errorCode,
                            LocalDateTime.now()
                    ));
                }
            }

            finalizationService.finalizeIfTerminal(attempt.getId());
        }
    }

    private void persistProfileSignals(
            ListeningTaskResponse response,
            ListeningTaskEvaluation evaluation,
            AiListeningContract.TaskResult result,
            AiListeningContract.EvaluationResponse provider
    ) {
        if (result.profileSignals() == null) {
            return;
        }

        ListeningItemAttempt attempt = response.getAttempt();

        for (AiListeningContract.ProfileSignal signal : result.profileSignals()) {
            ListeningProfileMetric metric;

            try {
                metric = ListeningProfileMetric.valueOf(signal.metric());
            } catch (RuntimeException exception) {
                continue;
            }

            String referenceEvaluationId = evaluation.getId() + ":"
                    + provider.evaluationVersion();

            if (historyRepository.existsByReferenceEvaluationIdAndMetricType(
                    referenceEvaluationId,
                    metric
            )) {
                continue;
            }

            boolean eligible = result.profileEligible()
                    && contractPolicy.profileEligible(
                            attempt.isOfficial(),
                            attempt.isPractice(),
                            attempt.isAnswerRevealed(),
                            response.isExcludedFromEvaluation(),
                            result.evaluable(),
                            signal.score(),
                            signal.confidence(),
                            signal.evidenceWeight()
                    );
            int rank = Math.min(
                    ListeningProfilePolicy.MAX_ACTIVITIES,
                    historyRepository
                            .findTop30ByUserIdAndLearningLanguageAndMetricTypeAndProfileAppliedTrueOrderByCreatedAtDesc(
                                    attempt.getSession().getUser().getId(),
                                    attempt.getItem().getDailySet()
                                            .getLearningLanguage(),
                                    metric
                            ).size() + 1
            );
            double recency = profilePolicy.recencyWeight(rank);
            double assistance = profilePolicy.assistanceWeight(
                    response.getAssistanceLevel()
            );
            double finalWeight = eligible
                    ? profilePolicy.finalWeight(
                            rank,
                            signal.confidence(),
                            response.getAssistanceLevel(),
                            signal.evidenceWeight()
                    )
                    : 0;
            historyRepository.save(ListeningMetricHistory.create(
                    attempt.getSession().getUser(),
                    attempt.getItem().getDailySet().getLearningLanguage(),
                    response.getTaskType(),
                    metric,
                    signal.score(),
                    signal.confidence(),
                    recency,
                    assistance,
                    signal.evidenceWeight(),
                    finalWeight,
                    response.getAssistanceLevel(),
                    "LISTENING_ATTEMPT:" + attempt.getId(),
                    referenceEvaluationId,
                    attempt.isOfficial(),
                    attempt.isPractice(),
                    eligible,
                    provider.evaluationVersion(),
                    provider.profilePolicyVersion()
            ));
        }
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record EvaluationWork(
            ListeningOutboxTransactionService.ClaimedEvent event,
            Long responseId,
            Long attemptId,
            Long itemId,
            ListeningTaskType taskType,
            String requestId,
            String profilePolicyVersion,
            Object request,
            String userAudioObjectKey,
            String audioContentType
    ) {
    }
}
