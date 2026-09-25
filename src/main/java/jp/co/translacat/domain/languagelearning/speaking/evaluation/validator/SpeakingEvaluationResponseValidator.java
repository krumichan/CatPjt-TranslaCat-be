package jp.co.translacat.domain.languagelearning.speaking.evaluation.validator;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingMetricDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SpeakingEvaluationResponseValidator {

    public void validate(
            AiSpeakingEvaluationResponseDto response,
            List<SpeakingTurn> turns
    ) {
        Set<String> turnIds = turns.stream().filter(turn -> turn.getId() != null)
                .map(turn -> String.valueOf(turn.getId())).collect(Collectors.toSet());
        validateResponse(response, turnIds);
    }

    /**
     * Compare against the immutable submitted request, not later mutable entity state.
     */
    public void validate(AiSpeakingEvaluationResponseDto response, AiSpeakingEvaluationRequestDto request) {
        if (response == null || request == null
                || !Objects.equals(response.sessionId(), request.sessionId())
                || !Objects.equals(response.requestId(), request.requestId())) {
            throw invalid("Speaking 평가 응답의 Request/Session이 일치하지 않습니다.");
        }
        validateResponse(response, request.userTurns().stream()
                .filter(turn -> !turn.excludedFromEvaluation())
                .map(turn -> turn.turnId()).collect(Collectors.toSet()));
        validateEvidencePolicy(response, request);
    }

    private void validateResponse(AiSpeakingEvaluationResponseDto response, Set<String> turnIds) {
        if (response == null) throw invalid("Speaking 평가 응답이 없습니다.");
        boolean evaluated = "EVALUATED".equalsIgnoreCase(response.status());
        boolean insufficient = "INSUFFICIENT_EVIDENCE".equalsIgnoreCase(response.status());
        if (!evaluated && !insufficient) throw invalid("지원하지 않는 Speaking 평가 상태입니다.");
        requireVersion(response.evaluationVersion(), 100);
        requireVersion(response.scoringPolicyVersion(), 100);
        requireVersion(response.promptVersion(), 100);
        validateConfidence(response.evaluationConfidence());
        if (evaluated) {
            validateScore("overallScore", response.overallScore());
            if (response.evaluationConfidence() == null) throw invalid("평가 Confidence가 필요합니다.");
        } else {
            if (response.overallScore() != null) throw invalid("증거 부족 응답에는 종합 점수가 없어야 합니다.");
            if (response.profileSignals() != null && !response.profileSignals().isEmpty())
                throw invalid("증거 부족 응답은 학습 Profile에 반영할 수 없습니다.");
        }
        // AI's deterministic precheck is a normal terminal result and intentionally has no metrics.
        if (insufficient && response.metrics() != null && response.metrics().isEmpty()) {
            var eligibility = response.eligibility();
            if (response.evaluationConfidence() != null || eligibility == null || eligibility.eligibleBeforeAi()
                    || eligibility.missingRequirements() == null || eligibility.missingRequirements().isEmpty())
                throw invalid("지표가 없는 증거 부족 응답에는 사전검사 실패 사유가 필요합니다.");
            validateEligibility(response);
            return;
        }
        validateMetrics(response.metrics(), turnIds);
        validateEligibility(response);
        if (evaluated && response.metrics().stream().noneMatch(metric -> "EVALUATED".equalsIgnoreCase(metric.state())))
            throw invalid("평가 가능한 Metric 없이 종합 점수를 저장할 수 없습니다.");
    }

    private void requireVersion(String value, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength)
            throw invalid("Speaking 평가 Version이 없거나 너무 깁니다.");
    }

    private void validateEvidencePolicy(AiSpeakingEvaluationResponseDto response,
                                        AiSpeakingEvaluationRequestDto request) {
        // A durable job can contain an older request created before evidence-v2.
        // Only the new request policy may require v2; it must not silently fall
        // back to legacy validation when a new AI response omits metadata.
        if (response.evidencePolicyVersion() == null) {
            if ("speaking-evaluation-policy-v2".equals(request.evaluationPolicyVersion()))
                throw invalid("새 Speaking 평가 응답에는 evidence 정책 버전이 필요합니다.");
            return;
        }
        if (!"speaking-transcript-evidence-v2".equals(response.evidencePolicyVersion())
                || !"TRANSCRIPT_OBSERVATION".equals(response.evidenceSource()))
            throw invalid("지원하지 않는 Speaking evidence 정책입니다.");
        Set<String> evaluated = response.metrics().stream()
                .filter(metric -> "EVALUATED".equals(metric.state()))
                .map(metric -> metric.type().name()).collect(Collectors.toSet());
        if (response.evaluatedAxes() == null
                || response.evaluatedAxes().size() != evaluated.size()
                || !evaluated.equals(Set.copyOf(response.evaluatedAxes())))
            throw invalid("Speaking 평가 축 metadata가 실제 Metric과 다릅니다.");
        double expectedCoverage = evaluated.stream().mapToDouble(axis -> switch (axis) {
            case "FLUENCY" -> 0.20;
            case "PRONUNCIATION", "INTERACTION" -> 0.15;
            default -> 0.10;
        }).sum();
        if (response.evaluationCoverage() == null || !Double.isFinite(response.evaluationCoverage())
                || Math.abs(response.evaluationCoverage() - expectedCoverage) > 0.0001)
            throw invalid("Speaking 평가 coverage가 실제 Metric과 다릅니다.");
        if (evaluated.contains("PRONUNCIATION") || evaluated.contains("FLUENCY")
                || response.pronunciationPractice() != null && !response.pronunciationPractice().isEmpty())
            throw invalid("텍스트 평가에는 음향 분석 근거가 없습니다.");
        boolean readAloud = request.practiceMode()
                == jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode.READ_ALOUD;
        if (readAloud && (evaluated.stream().anyMatch(axis -> !"MEANING".equals(axis))
                || response.profileSignals() != null && !response.profileSignals().isEmpty()))
            throw invalid("따라 읽기에서 자발적 언어 능력 Profile을 생성할 수 없습니다.");
        Set<String> usable = request.userTurns().stream()
                .filter(turn -> !turn.excludedFromEvaluation() && turn.transcript() != null
                        && !turn.transcript().isBlank() && turn.sttConfidence() >= .55)
                .filter(turn -> turn.segments() == null || turn.segments().stream()
                        .allMatch(segment -> segment.confidence() >= .55))
                .map(turn -> turn.turnId()).collect(Collectors.toSet());
        response.metrics().stream().filter(metric -> "EVALUATED".equals(metric.state())).forEach(metric -> {
            if (metric.evidence() == null || metric.evidence().isEmpty()
                    || metric.evidence().stream().anyMatch(evidence -> !usable.contains(evidence.turnId())))
                throw invalid("불확실한 인식 결과만으로 Speaking Metric을 평가할 수 없습니다.");
        });
        if (response.profileSignals() != null) response.profileSignals().forEach(signal -> {
            if (!evaluated.contains(signal.metricType().name()) || signal.evidenceTurnIds() == null
                    || signal.evidenceTurnIds().stream().distinct().count() < 2
                    || !usable.containsAll(signal.evidenceTurnIds()))
                throw invalid("Speaking Profile에 신뢰 가능한 복수의 텍스트 근거가 필요합니다.");
        });
    }

    private void validateEligibility(AiSpeakingEvaluationResponseDto response) {
        var eligibility = response.eligibility();
        if (eligibility == null) return; // Older scored response fixtures did not include this optional metadata.
        if (eligibility.validUserTurns() < 0 || !Double.isFinite(eligibility.validUserSpeechSeconds())
                || eligibility.validUserSpeechSeconds() < 0) throw invalid("평가 증거 수치가 유효하지 않습니다.");
        validateConfidence(eligibility.validSttTurnRatio());
    }

    private void validateMetrics(
            List<AiSpeakingMetricDto> metrics,
            Set<String> turnIds
    ) {
        if (metrics == null || metrics.size() != SpeakingMetricType.values().length) {
            throw invalid("Speaking 8대 평가 Metric이 모두 필요합니다.");
        }

        Set<SpeakingMetricType> expected = EnumSet.allOf(SpeakingMetricType.class);
        Set<SpeakingMetricType> found = EnumSet.noneOf(SpeakingMetricType.class);
        for (AiSpeakingMetricDto metric : metrics) {
            if (metric == null || metric.type() == null || !found.add(metric.type())) {
                throw invalid("Speaking 평가 Metric Type이 중복되거나 누락되었습니다.");
            }
            validateConfidence(metric.confidence());
            validateMetricState(metric);
            validateEvidence(metric, turnIds);
        }

        if (!found.equals(expected)) {
            throw invalid("Speaking 평가 Metric 구성이 유효하지 않습니다.");
        }
    }

    private void validateMetricState(AiSpeakingMetricDto metric) {
        if ("NOT_EVALUABLE".equalsIgnoreCase(metric.state())) {
            if (metric.score() != null) {
                throw invalid("NOT_EVALUABLE Metric에는 점수를 저장할 수 없습니다.");
            }
            return;
        }
        if (!"EVALUATED".equalsIgnoreCase(metric.state())) {
            throw invalid("지원하지 않는 Speaking Metric 상태입니다.");
        }
        validateScore(metric.type().name(), metric.score());
    }

    private void validateEvidence(
            AiSpeakingMetricDto metric,
            Set<String> turnIds
    ) {
        if (metric.evidence() == null) {
            return;
        }
        metric.evidence().forEach(evidence -> {
            if (evidence == null || evidence.turnId() == null) {
                return;
            }
            if (!turnIds.contains(evidence.turnId())) {
                throw invalid("Speaking 평가 Evidence가 Session Turn과 일치하지 않습니다.");
            }
        });
    }

    private void validateScore(String field, Number value) {
        if (value == null) {
            throw invalid(field + "가 필요합니다.");
        }
        double score = value.doubleValue();
        if (!Double.isFinite(score) || score < 0 || score > 100) {
            throw invalid(field + "는 0~100 범위여야 합니다.");
        }
    }

    private void validateConfidence(Number value) {
        if (value == null) {
            return;
        }
        double confidence = value.doubleValue();
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw invalid("Speaking 평가 Confidence는 0~1 범위여야 합니다.");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.SPEAKING_EVALUATION_FAILED
        );
    }
}
