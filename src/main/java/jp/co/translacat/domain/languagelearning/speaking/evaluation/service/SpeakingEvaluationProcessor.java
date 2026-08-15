package jp.co.translacat.domain.languagelearning.speaking.evaluation.service;

import jp.co.translacat.domain.languagelearning.activity.entity.EvaluationMetricHistory;
import jp.co.translacat.domain.languagelearning.activity.entity.LearningActivity;
import jp.co.translacat.domain.languagelearning.activity.repository.EvaluationMetricHistoryRepository;
import jp.co.translacat.domain.languagelearning.activity.repository.LearningActivityRepository;
import jp.co.translacat.domain.languagelearning.common.enums.LearningActivityStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.enums.MetricEvaluationState;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.profile.service.SpeakingProfileSignalService;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingMetricDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.port.SpeakingAiClient;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluationMetric;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.factory.SpeakingEvaluationRequestFactory;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.policy.SpeakingEvaluationEligibilityPolicy;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationMetricRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.validator.SpeakingEvaluationResponseValidator;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;
import jp.co.translacat.domain.languagelearning.speaking.usage.service.SpeakingAiUsageCommandService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeakingEvaluationProcessor {

    private final SpeakingSessionRepository sessionRepository;
    private final SpeakingTurnRepository turnRepository;
    private final SpeakingEvaluationRepository evaluationRepository;
    private final SpeakingEvaluationMetricRepository metricRepository;
    private final SpeakingEvaluationEligibilityPolicy eligibilityPolicy;
    private final SpeakingEvaluationRequestFactory requestFactory;
    private final SpeakingEvaluationResponseValidator responseValidator;
    private final SpeakingAiClient speakingAiClient;
    private final LanguageLearningJsonCodec jsonCodec;
    private final LearningActivityRepository activityRepository;
    private final EvaluationMetricHistoryRepository metricHistoryRepository;
    private final SpeakingProfileSignalService profileSignalService;
    private final SpeakingAiUsageCommandService usageCommandService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long sessionId, int manualRetryAttempt) {
        SpeakingSession session = sessionRepository.findById(sessionId)
                .orElseThrow();
        String version = SpeakingEvaluationRequestFactory.EVALUATION_POLICY_VERSION;
        if (evaluationRepository
                .findBySessionIdAndEvaluationVersion(sessionId, version)
                .isPresent()) {
            return;
        }

        List<SpeakingTurn> turns = turnRepository
                .findAllBySessionIdOrderByTurnIndexAsc(sessionId);
        var eligibility = eligibilityPolicy.evaluate(turns);
        LearningActivity activity = activityRepository
                .findBySourceAndReferenceId(
                        LearningSource.SPEAKING,
                        String.valueOf(sessionId)
                )
                .orElseThrow();

        if (!eligibility.eligibleBeforeAi()) {
            SpeakingEvaluation evaluation = saveInsufficientBeforeAi(
                    session,
                    eligibility
            );
            activity.markInsufficientEvidence(null);
            session.markInsufficientEvidence(evaluation.getEvaluationVersion());
            return;
        }

        session.markEvaluating();
        activity.markEvaluating();
        AiSpeakingEvaluationResponseDto response;
        try {
            response = speakingAiClient.evaluate(
                    requestFactory.create(
                            session,
                            turns,
                            manualRetryAttempt
                    )
            );
        } catch (RuntimeException e) {
            session.markEvaluationFailed();
            activity.markEvaluationFailed();
            throw e;
        }

        usageCommandService.record(
                session,
                null,
                response == null ? null : response.usage(),
                manualRetryAttempt
        );
        try {
            responseValidator.validate(response, turns);
        } catch (RuntimeException e) {
            session.markEvaluationFailed();
            activity.markEvaluationFailed();
            throw e;
        }

        boolean formal = response != null
                && "EVALUATED".equalsIgnoreCase(response.status())
                && eligibilityPolicy.hasFormalEvaluationConfidence(
                        response.evaluationConfidence()
                );
        SpeakingEvaluation evaluation = saveEvaluation(
                session,
                response,
                formal
        );
        saveSpeakingMetrics(evaluation, response);

        if (!formal) {
            activity.markInsufficientEvidence(
                    response == null ? null : response.evaluationConfidence()
            );
            session.markInsufficientEvidence(
                    evaluation.getEvaluationVersion()
            );
            return;
        }

        activity.markEvaluated(
                response.overallScore(),
                response.evaluationConfidence()
        );
        saveMetricHistory(activity, response.metrics());
        double activityWeight = resolveActivityWeight(response, turns);
        profileSignalService.apply(
                session.getUser().getId(),
                response.profileSignals(),
                activityWeight
        );
        session.markEvaluated(evaluation.getEvaluationVersion());
    }

    private SpeakingEvaluation saveInsufficientBeforeAi(
            SpeakingSession session,
            Object eligibility
    ) {
        return evaluationRepository.save(
                SpeakingEvaluation.create(
                        session,
                        null,
                        null,
                        SpeakingEvaluationRequestFactory.EVALUATION_POLICY_VERSION,
                        "speaking-scoring-policy-v1",
                        "not-called",
                        "INSUFFICIENT_EVIDENCE",
                        "[]",
                        "[]",
                        "[]",
                        "[]",
                        "[]",
                        jsonCodec.write(eligibility),
                        "{}"
                )
        );
    }

    private SpeakingEvaluation saveEvaluation(
            SpeakingSession session,
            AiSpeakingEvaluationResponseDto response,
            boolean formal
    ) {
        if (response == null) {
            return evaluationRepository.save(
                    SpeakingEvaluation.create(
                            session,
                            null,
                            null,
                            SpeakingEvaluationRequestFactory.EVALUATION_POLICY_VERSION,
                            "speaking-scoring-policy-v1",
                            "unknown",
                            "INSUFFICIENT_EVIDENCE",
                            "[]", "[]", "[]", "[]", "[]", "{}", "{}"
                    )
            );
        }
        return evaluationRepository.save(
                SpeakingEvaluation.create(
                        session,
                        formal ? response.overallScore() : null,
                        response.evaluationConfidence(),
                        response.evaluationVersion() == null
                                ? SpeakingEvaluationRequestFactory.EVALUATION_POLICY_VERSION
                                : response.evaluationVersion(),
                        response.scoringPolicyVersion() == null
                                ? "speaking-scoring-policy-v1"
                                : response.scoringPolicyVersion(),
                        response.promptVersion() == null
                                ? "unknown"
                                : response.promptVersion(),
                        formal ? "EVALUATED" : "INSUFFICIENT_EVIDENCE",
                        jsonCodec.write(response.strengths()),
                        jsonCodec.write(response.improvements()),
                        jsonCodec.write(response.recommendedExpressions()),
                        jsonCodec.write(response.pronunciationPractice()),
                        jsonCodec.write(response.profileSignals()),
                        jsonCodec.write(response.eligibility()),
                        jsonCodec.write(response.usage())
                )
        );
    }

    private void saveSpeakingMetrics(
            SpeakingEvaluation evaluation,
            AiSpeakingEvaluationResponseDto response
    ) {
        if (response == null || response.metrics() == null) {
            return;
        }
        List<SpeakingEvaluationMetric> metrics = response.metrics().stream()
                .map(metric -> SpeakingEvaluationMetric.create(
                        evaluation,
                        metric.type(),
                        metricState(metric),
                        metric.score(),
                        metric.confidence(),
                        metric.summary(),
                        metric.notEvaluableReason(),
                        jsonCodec.write(metric.evidence())
                ))
                .toList();
        metricRepository.saveAll(metrics);
    }

    private void saveMetricHistory(
            LearningActivity activity,
            List<AiSpeakingMetricDto> metrics
    ) {
        if (metrics == null) {
            return;
        }
        metricHistoryRepository.saveAll(
                metrics.stream()
                        .map(metric -> EvaluationMetricHistory.create(
                                activity,
                                WritingMetric.valueOf(metric.type().name()),
                                metricState(metric),
                                metric.score(),
                                metric.confidence(),
                                metric.notEvaluableReason()
                        ))
                        .toList()
        );
    }

    private MetricEvaluationState metricState(AiSpeakingMetricDto metric) {
        return "NOT_EVALUABLE".equalsIgnoreCase(metric.state())
                ? MetricEvaluationState.NOT_EVALUABLE
                : MetricEvaluationState.EVALUATED;
    }

    private double resolveActivityWeight(
            AiSpeakingEvaluationResponseDto response,
            List<SpeakingTurn> turns
    ) {
        double confidence = response.evaluationConfidence() == null
                ? 0
                : response.evaluationConfidence();
        double assistanceWeight = assistanceWeight(turns);
        double validity = response.eligibility() == null
                ? 1.0
                : Math.min(1.0, response.eligibility().validSttTurnRatio());
        return round(confidence * assistanceWeight * validity);
    }

    private double assistanceWeight(List<SpeakingTurn> turns) {
        boolean guided = turns.stream()
                .map(SpeakingTurn::getAssistanceUsageJson)
                .anyMatch(value -> value != null
                        && value.contains("SAMPLE_ANSWER"));
        if (guided) {
            return 0.60;
        }
        boolean assisted = turns.stream()
                .map(SpeakingTurn::getAssistanceUsageJson)
                .anyMatch(value -> value != null
                        && (value.contains("HINT")
                        || value.contains("TRANSLATION")));
        return assisted ? 0.80 : 1.0;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
