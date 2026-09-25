package jp.co.translacat.domain.languagelearning.speaking.evaluation.service;

import jp.co.translacat.domain.languagelearning.activity.model.GrowthActivityDraft;
import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.common.enums.MetricEvaluationState;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthFacts;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthOperation;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthCommands;
import jp.co.translacat.domain.languagelearning.resultjournal.model.LearningResultCaptured;
import jp.co.translacat.domain.languagelearning.resultjournal.model.SpeakingResultFact;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingMetricDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluationMetric;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.policy.SpeakingEvaluationEligibilityPolicy;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.policy.SpeakingEvidenceMetadata;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationMetricRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SpeakingEvaluationResultCommandService {

    private final SpeakingTurnRepository turnRepository;
    private final SpeakingEvaluationRepository evaluationRepository;
    private final SpeakingEvaluationMetricRepository metricRepository;
    private final SpeakingEvaluationEligibilityPolicy eligibilityPolicy;
    private final LanguageLearningJsonCodec jsonCodec;
    private final ApplicationEventPublisher resultEvents;
    private final GrowthCommands growthCommands;
    private final LearningActivityCommandService activityCommands;

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(SpeakingSession session, AiSpeakingEvaluationResponseDto response) {
        if (session.getResultKind()
                != jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingResultKind.SCORED_EVALUATION) {
            throw new IllegalArgumentException("FREE 코칭은 정식 평가 저장 경로를 사용할 수 없습니다.");
        }
        // Session is locked by the job command. A result is final regardless of the AI version label.
        // This prevents a replay from duplicating metric history and profile signals.
        var existing = evaluationRepository.findFirstBySessionIdOrderByEvaluatedAtDesc(session.getId());
        if (existing.isPresent()) {
            SpeakingEvaluation saved = existing.get();
            if ("EVALUATED".equals(saved.getStatus())) session.markEvaluated(saved.getEvaluationVersion());
            else session.markInsufficientEvidence(saved.getEvaluationVersion());
            return;
        }
        List<SpeakingTurn> turns = turnRepository.findAllBySessionIdOrderByTurnIndexAsc(session.getId());
        GrowthActivityDraft activity = activityCommands.speakingResult(session);
        boolean formal =
                "EVALUATED".equalsIgnoreCase(response.status()) && eligibilityPolicy.hasFormalEvaluationConfidence(
                        response.evaluationConfidence());
        SpeakingEvaluation evaluation = evaluationRepository.save(
                SpeakingEvaluation.create(session, formal ? response.overallScore() : null,
                        response.evaluationConfidence(), response.evaluationVersion(), response.scoringPolicyVersion(),
                        response.promptVersion(), formal ? "EVALUATED" : "INSUFFICIENT_EVIDENCE",
                        jsonCodec.write(response.strengths()), jsonCodec.write(response.improvements()),
                        jsonCodec.write(response.recommendedExpressions()),
                        jsonCodec.write(response.pronunciationPractice()), jsonCodec.write(response.profileSignals()),
                        SpeakingEvidenceMetadata.eligibilitySnapshot(response, jsonCodec),
                        jsonCodec.write(response.usage())));
        saveSpeakingMetrics(evaluation, response);
        if (!formal) {
            activity.markInsufficientEvidence(response.evaluationConfidence());
            session.markInsufficientEvidence(evaluation.getEvaluationVersion());
            publishResult(session, evaluation, activity, response, false, 0.0);
            return;
        }
        activity.markEvaluated(response.overallScore(), response.evaluationConfidence());
        double activityWeight = resolveActivityWeight(response, turns);
        session.markEvaluated(evaluation.getEvaluationVersion());
        publishResult(session, evaluation, activity, response, true, activityWeight);
    }

    private void publishResult(SpeakingSession session, SpeakingEvaluation evaluation, GrowthActivityDraft activity,
                               AiSpeakingEvaluationResponseDto response, boolean formal, double activityWeight) {
        // Session 평가와 성장 명령은 같은 Core 커밋이다. 실제 성장 반영은 LL에서 원자적으로 처리한다.
        var metrics = response.metrics() == null ? List.<Map<String, Object>>of() : response.metrics()
                .stream()
                .map(metric -> GrowthFacts.fields("metricType", metric.type().name(), "state",
                        metricState(metric).name(), "score", metric.score(), "confidence", metric.confidence(),
                        "notEvaluableReason", GrowthFacts.nonBlankOrNull(metric.notEvaluableReason())))
                .toList();
        var evidence = response.profileSignals() == null ? List.<Map<String, Object>>of() : response.profileSignals()
                .stream()
                .filter(value -> value != null && value.patternKey() != null && !value.patternKey().isBlank())
                .map(value -> GrowthFacts.fields("metricType",
                        value.metricType() == null ? null : value.metricType().name(), "patternKey", value.patternKey(),
                        "direction", GrowthFacts.nonBlankOrNull(value.direction()), "confidence", value.confidence(),
                        "recommendedFocus", GrowthFacts.nonBlankOrNull(value.recommendedFocus())))
                .toList();
        growthCommands.append(session.getUser().getId(),
                new GrowthOperation("SPEAKING_EVALUATION:" + evaluation.getId(), "SPEAKING_SCORED",
                        GrowthFacts.fields("resultKind", session.getResultKind().name(), "activity", activity.payload(),
                                "metrics", metrics, "formal", formal, "activityWeight", activityWeight, "evidence",
                                evidence)));
        // 기존 선택적 raw journal은 진단 보관만 유지한다. LL 활동 ID를 Core에서 만들어 내지 않는다.
        resultEvents.publishEvent(new LearningResultCaptured(session.getUser().getId(),
                formal ? "SPEAKING_SCORED" : "SPEAKING_INSUFFICIENT", session.getId().toString(), jsonCodec.write(
                new SpeakingResultFact(session.getResultKind().name(), evaluation.getId(), session.getId(), null,
                        session.getLearningDate().toString(), session.getOriginLanguage(),
                        session.getLearningLanguage(), formal, activityWeight, response))));
    }

    private void saveSpeakingMetrics(SpeakingEvaluation evaluation, AiSpeakingEvaluationResponseDto response) {
        if (response == null || response.metrics() == null) {
            return;
        }
        List<SpeakingEvaluationMetric> metrics = response.metrics()
                .stream()
                .map(metric -> SpeakingEvaluationMetric.create(evaluation, metric.type(), metricState(metric),
                        metric.score(), metric.confidence(), metric.summary(), metric.notEvaluableReason(),
                        jsonCodec.write(metric.evidence())))
                .toList();
        metricRepository.saveAll(metrics);
    }

    private MetricEvaluationState metricState(AiSpeakingMetricDto metric) {
        return "NOT_EVALUABLE".equalsIgnoreCase(metric.state()) ? MetricEvaluationState.NOT_EVALUABLE :
                MetricEvaluationState.EVALUATED;
    }

    private double resolveActivityWeight(AiSpeakingEvaluationResponseDto response, List<SpeakingTurn> turns) {
        double confidence = response.evaluationConfidence() == null ? 0 : response.evaluationConfidence();
        double assistanceWeight = assistanceWeight(turns);
        double validity =
                response.eligibility() == null ? 1.0 : Math.min(1.0, response.eligibility().validSttTurnRatio());
        return round(confidence * assistanceWeight * validity);
    }

    private double assistanceWeight(List<SpeakingTurn> turns) {
        boolean guided = turns.stream()
                .map(SpeakingTurn::getAssistanceUsageJson)
                .anyMatch(value -> value != null && value.contains("SAMPLE_ANSWER"));
        if (guided) {
            return 0.60;
        }
        boolean assisted = turns.stream()
                .map(SpeakingTurn::getAssistanceUsageJson)
                .anyMatch(value -> value != null && (value.contains("HINT") || value.contains("TRANSLATION")));
        return assisted ? 0.80 : 1.0;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
