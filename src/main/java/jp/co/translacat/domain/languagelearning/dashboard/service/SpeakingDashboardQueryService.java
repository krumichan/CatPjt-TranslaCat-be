package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.common.enums.MetricEvaluationState;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.SpeakingFeatureSummaryResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.SpeakingTodayProgressResponseDto;
import jp.co.translacat.domain.languagelearning.profile.policy.LearningProfileAggregationWeightPolicy;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingSessionStatus;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluationMetric;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationMetricRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakingDashboardQueryService {

    private final SpeakingSessionRepository sessionRepository;
    private final SpeakingEvaluationRepository evaluationRepository;
    private final SpeakingEvaluationMetricRepository metricRepository;

    public SpeakingTodayProgressResponseDto getToday(
            Long userId,
            LocalDate today,
            int goalMinutes
    ) {
        List<SpeakingSession> sessions = sessionRepository
                .findAllByUserIdAndLearningDate(userId, today)
                .stream()
                .filter(session -> !session.isActive())
                .filter(session -> session.getStatus() != SpeakingSessionStatus.EXPIRED)
                .toList();
        double minutes = round(sessions.stream()
                .mapToLong(SpeakingSession::getTotalDurationSeconds)
                .sum() / 60.0);
        String status = minutes >= goalMinutes
                ? "COMPLETED"
                : minutes > 0 ? "IN_PROGRESS" : "NOT_STARTED";
        return new SpeakingTodayProgressResponseDto(
                sessions.size(),
                minutes,
                goalMinutes,
                status
        );
    }

    public SpeakingFeatureSummaryResponseDto getSummary(
            Long userId,
            LocalDate from,
            LocalDate to
    ) {
        List<SpeakingSession> sessions = sessionRepository
                .findAllByUserIdAndLearningDateBetweenOrderByLearningDateDescStartedAtDesc(
                        userId,
                        from,
                        to
                );
        List<SpeakingEvaluation> evaluations = evaluationRepository
                .findAllBySessionUserIdOrderByEvaluatedAtDesc(userId)
                .stream()
                .filter(evaluation -> evaluation.getOverallScore() != null)
                .filter(evaluation -> !evaluation.getSession().getLearningDate().isBefore(from))
                .filter(evaluation -> !evaluation.getSession().getLearningDate().isAfter(to))
                .toList();
        List<SpeakingEvaluationMetric> metrics = evaluations.stream()
                .flatMap(evaluation -> metricRepository
                        .findAllByEvaluationIdOrderByMetricTypeAsc(evaluation.getId())
                        .stream())
                .toList();

        return new SpeakingFeatureSummaryResponseDto(
                (int) sessions.stream()
                        .filter(session -> !session.isActive())
                        .filter(session -> session.getStatus() != SpeakingSessionStatus.EXPIRED)
                        .count(),
                round(sessions.stream()
                        .filter(session -> session.getStatus() != SpeakingSessionStatus.EXPIRED)
                        .mapToLong(SpeakingSession::getTotalDurationSeconds)
                        .sum() / 60.0),
                averageEvaluation(evaluations),
                metricAverage(metrics, SpeakingMetricType.FLUENCY),
                metricAverage(metrics, SpeakingMetricType.PRONUNCIATION),
                metricAverage(metrics, SpeakingMetricType.INTERACTION),
                evaluations.size()
                        < LearningProfileAggregationWeightPolicy.COLLECTING_DATA_THRESHOLD
        );
    }

    private Double averageEvaluation(List<SpeakingEvaluation> evaluations) {
        if (evaluations.isEmpty()) {
            return null;
        }
        return round(evaluations.stream()
                .map(SpeakingEvaluation::getOverallScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0));
    }

    private Double metricAverage(
            List<SpeakingEvaluationMetric> metrics,
            SpeakingMetricType type
    ) {
        List<Double> values = metrics.stream()
                .filter(metric -> metric.getMetricType() == type)
                .filter(metric -> metric.getState() == MetricEvaluationState.EVALUATED)
                .map(SpeakingEvaluationMetric::getScore)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        return round(values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
