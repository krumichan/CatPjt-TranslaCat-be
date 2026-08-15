package jp.co.translacat.domain.languagelearning.speaking.evaluation.validator;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingMetricDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SpeakingEvaluationResponseValidator {

    public void validate(
            AiSpeakingEvaluationResponseDto response,
            List<SpeakingTurn> turns
    ) {
        if (response == null) {
            throw invalid("Speaking 평가 응답이 없습니다.");
        }
        validateConfidence(response.evaluationConfidence());
        validateMetrics(response.metrics(), turns);

        if (isEvaluated(response.status())) {
            validateScore("overallScore", response.overallScore());
        }
    }

    private void validateMetrics(
            List<AiSpeakingMetricDto> metrics,
            List<SpeakingTurn> turns
    ) {
        if (metrics == null || metrics.size() != SpeakingMetricType.values().length) {
            throw invalid("Speaking 8대 평가 Metric이 모두 필요합니다.");
        }

        Set<SpeakingMetricType> expected = EnumSet.allOf(SpeakingMetricType.class);
        Set<SpeakingMetricType> found = EnumSet.noneOf(SpeakingMetricType.class);
        Set<String> turnIds = new HashSet<>();
        for (SpeakingTurn turn : turns) {
            if (turn.getId() != null) {
                turnIds.add(String.valueOf(turn.getId()));
            }
        }

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
        if (score < 0 || score > 100) {
            throw invalid(field + "는 0~100 범위여야 합니다.");
        }
    }

    private void validateConfidence(Number value) {
        if (value == null) {
            return;
        }
        double confidence = value.doubleValue();
        if (confidence < 0 || confidence > 1) {
            throw invalid("Speaking 평가 Confidence는 0~1 범위여야 합니다.");
        }
    }

    private boolean isEvaluated(String status) {
        return "EVALUATED".equalsIgnoreCase(status);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.SPEAKING_EVALUATION_FAILED
        );
    }
}
