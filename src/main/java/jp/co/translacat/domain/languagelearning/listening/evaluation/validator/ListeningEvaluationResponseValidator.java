package jp.co.translacat.domain.languagelearning.listening.evaluation.validator;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningMetricType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningEvaluationContractPolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningProfilePolicy;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ListeningEvaluationResponseValidator {

    private final ListeningEvaluationContractPolicy contractPolicy;

    public AiListeningContract.TaskResult validate(
            AiListeningContract.EvaluationResponse response,
            String requestId,
            String profilePolicyVersion,
            Long itemId,
            Long attemptId,
            ListeningTaskType taskType
    ) {
        if (response == null
                || !requestId.equals(response.requestId())
                || !itemId.equals(response.itemId())
                || !attemptId.equals(response.attemptId())
                || blank(response.evaluationVersion())
                || blank(response.scoringPolicyVersion())
                || !profilePolicyVersion.equals(response.profilePolicyVersion())
                || response.tasks() == null
                || response.tasks().size() != ListeningTaskType.values().length) {
            throw invalid("Listening 평가 응답의 기본 계약이 올바르지 않습니다.");
        }
        Map<ListeningTaskType, AiListeningContract.TaskResult> tasks =
                new EnumMap<>(ListeningTaskType.class);
        for (AiListeningContract.TaskResult value : response.tasks()) {
            if (value == null || value.taskType() == null
                    || tasks.put(value.taskType(), value) != null) {
                throw invalid("Listening 평가 Task가 누락되었거나 중복되었습니다.");
            }
        }
        if (tasks.size() != ListeningTaskType.values().length) {
            throw invalid("Listening 평가 Task 목록이 완전하지 않습니다.");
        }
        tasks.forEach((type, value) -> {
            if (type != taskType) {
                validateNotSelected(value);
            }
        });
        AiListeningContract.TaskResult result = tasks.get(taskType);
        if (result == null) {
            throw invalid("요청한 Task 평가 결과가 없습니다.");
        }
        if (result.evaluable()) {
            if (!"EVALUATED".equals(result.status())) {
                throw invalid("평가 가능 Task 상태가 올바르지 않습니다.");
            }
            range(result.score(), 100, "score");
            range(result.confidence(), 1, "confidence");
        } else {
            if (!"NOT_EVALUABLE".equals(result.status())
                    || result.score() != null
                    || blank(result.reasonCode())) {
                throw invalid(
                        "평가 불가 결과에는 Score가 없어야 하고 사유가 필요합니다."
                );
            }
            if (result.confidence() != null) {
                range(result.confidence(), 1, "confidence");
            }
        }
        List<ListeningEvaluationContractPolicy.MetricProjection> metrics =
                safe(result.metrics()).stream()
                        .map(metric -> {
                            validateMetric(metric);
                            return new ListeningEvaluationContractPolicy.MetricProjection(
                                    parseMetric(metric.type()),
                                    metric.weight()
                            );
                        })
                        .toList();
        if (result.evaluable() || !metrics.isEmpty()) {
            contractPolicy.validateMetrics(taskType, metrics);
        }
        validateProfileSignals(result, response.profilePolicyVersion(), taskType);
        List<ListeningEvaluationContractPolicy.ProfileSignalProjection> signals =
                safe(result.profileSignals()).stream()
                        .map(signal -> new ListeningEvaluationContractPolicy.ProfileSignalProjection(
                                parseProfileMetric(signal.metric()),
                                signal.evidenceWeight()
                        ))
                        .toList();
        contractPolicy.validateProfileSignals(taskType, signals);
        validateOverall(response.overall(), result);
        return result;
    }

    private void validateNotSelected(AiListeningContract.TaskResult result) {
        if (!"NOT_SELECTED".equals(result.status())
                || result.evaluable()
                || result.score() != null
                || !safe(result.metrics()).isEmpty()
                || !safe(result.evidence()).isEmpty()
                || !safe(result.profileSignals()).isEmpty()
                || result.profileEligible()) {
            throw invalid("선택하지 않은 Listening Task에 평가 결과가 포함되었습니다.");
        }
    }

    private void validateMetric(AiListeningContract.Metric metric) {
        if (metric == null || blank(metric.state())) {
            throw invalid("Listening Metric 계약이 올바르지 않습니다.");
        }
        range(metric.confidence(), 1, "metric confidence");
        if ("EVALUATED".equals(metric.state())) {
            range(metric.score(), 100, "metric score");
        } else if (!"NOT_EVALUABLE".equals(metric.state())
                || metric.score() != null
                || blank(metric.notEvaluableReason())) {
            throw invalid("Listening Metric 평가 상태가 올바르지 않습니다.");
        }
    }

    private void validateProfileSignals(
            AiListeningContract.TaskResult result,
            String profilePolicyVersion,
            ListeningTaskType taskType
    ) {
        List<AiListeningContract.ProfileSignal> signals =
                safe(result.profileSignals());
        if (result.profileEligible() != !signals.isEmpty()) {
            throw invalid("Listening Profile 대상 여부와 Signal이 일치하지 않습니다.");
        }
        for (AiListeningContract.ProfileSignal signal : signals) {
            if (signal == null || signal.sourceTask() != taskType
                    || !profilePolicyVersion.equals(signal.policyVersion())) {
                throw invalid("Listening Profile Signal 출처가 올바르지 않습니다.");
            }
            range(signal.score(), 100, "profile score");
            range(signal.confidence(), 1, "profile confidence");
            if (signal.confidence() < ListeningProfilePolicy.MIN_CONFIDENCE) {
                throw invalid("Listening Profile confidence가 기준 미만입니다.");
            }
            if (signal.evidenceWeight() <= 0
                    || signal.evidenceWeight() > 1) {
                throw invalid("Listening Profile evidence weight 범위가 올바르지 않습니다.");
            }
        }
    }

    private void validateOverall(
            AiListeningContract.Overall overall,
            AiListeningContract.TaskResult selected
    ) {
        int evaluatedCount = selected.evaluable() ? 1 : 0;
        if (overall == null
                || overall.totalTaskCount() != ListeningTaskType.values().length
                || overall.evaluatedTaskCount() != evaluatedCount
                || (evaluatedCount == 0 && overall.score() != null)
                || (evaluatedCount == 1 && overall.score() == null)) {
            throw invalid("Listening Overall 평가 계약이 올바르지 않습니다.");
        }
        if (overall.score() != null) {
            range(overall.score(), 100, "overall score");
        }
    }

    private ListeningMetricType parseMetric(String value) {
        try {
            return ListeningMetricType.valueOf(value);
        } catch (RuntimeException exception) {
            throw invalid("알 수 없는 Listening Metric입니다.");
        }
    }

    private ListeningProfileMetric parseProfileMetric(String value) {
        try {
            return ListeningProfileMetric.valueOf(value);
        } catch (RuntimeException exception) {
            throw invalid("알 수 없는 Listening Profile Metric입니다.");
        }
    }

    private void range(Double value, double max, String field) {
        if (value == null || value < 0 || value > max) {
            throw invalid("Listening " + field + " 범위가 올바르지 않습니다.");
        }
    }

    private <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.AI_SCHEMA_INVALID
        );
    }
}
