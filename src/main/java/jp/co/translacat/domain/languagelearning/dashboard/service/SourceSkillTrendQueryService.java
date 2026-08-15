package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.activity.entity.EvaluationMetricHistory;
import jp.co.translacat.domain.languagelearning.activity.entity.LearningActivity;
import jp.co.translacat.domain.languagelearning.activity.repository.EvaluationMetricHistoryRepository;
import jp.co.translacat.domain.languagelearning.activity.repository.LearningActivityRepository;
import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LearningActivityStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.enums.MetricEvaluationState;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.MetricPointResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.SourceSkillTrendResponseDto;
import jp.co.translacat.domain.languagelearning.profile.policy.LearningProfileAggregationWeightPolicy;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceSkillTrendQueryService {

    private final WritingEvaluationRepository writingEvaluationRepository;
    private final LearningActivityRepository activityRepository;
    private final EvaluationMetricHistoryRepository metricHistoryRepository;
    private final LearningProfileAggregationWeightPolicy weightPolicy;

    public SourceSkillTrendResponseDto get(
            Long userId,
            LearningSource source,
            LocalDate from,
            LocalDate to
    ) {
        Map<String, Map<LocalDate, List<WeightedScore>>> scores =
                new LinkedHashMap<>();
        int samples = 0;
        double confidenceSum = 0;
        int confidenceCount = 0;

        if (source == null || source == LearningSource.WRITING) {
            List<WritingEvaluation> writing = writingEvaluationRepository
                    .findAllByUserIdAndContextAndStatusOrderByEvaluatedAtDesc(
                            userId,
                            WritingEvaluationContext.DAILY,
                            EvaluationStatus.SUCCESS
                    )
                    .stream()
                    .filter(evaluation -> !writingDate(evaluation).isBefore(from))
                    .filter(evaluation -> !writingDate(evaluation).isAfter(to))
                    .toList();
            samples += writing.size();
            addWriting(scores, writing);
        }

        if (source == null || source == LearningSource.SPEAKING) {
            List<LearningActivity> speaking = activityRepository
                    .findAllByUserIdAndSourceAndLearningDateBetweenOrderByLearningDateDesc(
                            userId,
                            LearningSource.SPEAKING,
                            from,
                            to
                    )
                    .stream()
                    .filter(activity -> activity.getStatus()
                            == LearningActivityStatus.EVALUATED)
                    .toList();
            samples += speaking.size();
            for (LearningActivity activity : speaking) {
                if (activity.getEvaluationConfidence() != null) {
                    confidenceSum += activity.getEvaluationConfidence();
                    confidenceCount++;
                }
            }
            addSpeaking(scores, speaking, userId, from, to);
        }

        return new SourceSkillTrendResponseDto(
                source == null ? "ALL" : source.name(),
                samples,
                confidenceCount == 0
                        ? 0
                        : round(confidenceSum / confidenceCount),
                samples < LearningProfileAggregationWeightPolicy.COLLECTING_DATA_THRESHOLD,
                toResponse(scores)
        );
    }

    private void addWriting(
            Map<String, Map<LocalDate, List<WeightedScore>>> target,
            List<WritingEvaluation> evaluations
    ) {
        for (int index = 0; index < evaluations.size(); index++) {
            WritingEvaluation evaluation = evaluations.get(index);
            double weight = weightPolicy.writingActivityWeight(
                    index,
                    evaluations.size()
            );
            LocalDate date = writingDate(evaluation);
            add(target, "MEANING", date, evaluation.getMeaningScore(), weight);
            add(target, "GRAMMAR", date, evaluation.getGrammarScore(), weight);
            add(target, "VOCABULARY", date, evaluation.getVocabularyScore(), weight);
            add(target, "NATURALNESS", date, evaluation.getNaturalnessScore(), weight);
            add(target, "EXPRESSION", date, evaluation.getExpressionScore(), weight);
        }
    }

    private void addSpeaking(
            Map<String, Map<LocalDate, List<WeightedScore>>> target,
            List<LearningActivity> activities,
            Long userId,
            LocalDate from,
            LocalDate to
    ) {
        Map<Long, Integer> order = new HashMap<>();
        for (int index = 0; index < activities.size(); index++) {
            order.put(activities.get(index).getId(), index);
        }
        Map<Long, LearningActivity> byId = new HashMap<>();
        activities.forEach(activity -> byId.put(activity.getId(), activity));

        List<EvaluationMetricHistory> histories = metricHistoryRepository
                .findAllByActivityUserIdAndActivityLearningDateBetween(
                        userId,
                        from,
                        to
                )
                .stream()
                .filter(history -> history.getActivity().getSource()
                        == LearningSource.SPEAKING)
                .filter(history -> history.getState()
                        == MetricEvaluationState.EVALUATED)
                .filter(history -> history.getScore() != null)
                .filter(history -> byId.containsKey(history.getActivity().getId()))
                .toList();

        for (EvaluationMetricHistory history : histories) {
            LearningActivity activity = byId.get(history.getActivity().getId());
            int index = order.get(activity.getId());
            double recency = weightPolicy.recencyWeight(index, activities.size());
            double confidence = activity.getEvaluationConfidence() == null
                    ? 0
                    : Math.max(0, Math.min(1, activity.getEvaluationConfidence()));
            add(
                    target,
                    history.getMetricType().name(),
                    activity.getLearningDate(),
                    history.getScore(),
                    recency * confidence
            );
        }
    }

    private Map<String, List<MetricPointResponseDto>> toResponse(
            Map<String, Map<LocalDate, List<WeightedScore>>> values
    ) {
        Map<String, List<MetricPointResponseDto>> result = new LinkedHashMap<>();
        values.forEach((metric, dates) -> result.put(
                metric,
                dates.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new MetricPointResponseDto(
                                entry.getKey(),
                                weightedAverage(entry.getValue())
                        ))
                        .toList()
        ));
        return result;
    }

    private void add(
            Map<String, Map<LocalDate, List<WeightedScore>>> target,
            String metric,
            LocalDate date,
            Number score,
            double weight
    ) {
        if (score == null || weight <= 0) {
            return;
        }
        target.computeIfAbsent(metric, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(date, ignored -> new ArrayList<>())
                .add(new WeightedScore(score.doubleValue(), weight));
    }

    private double weightedAverage(List<WeightedScore> values) {
        double denominator = values.stream()
                .mapToDouble(WeightedScore::weight)
                .sum();
        if (denominator <= 0) {
            return 0;
        }
        double numerator = values.stream()
                .mapToDouble(value -> value.score() * value.weight())
                .sum();
        return round(numerator / denominator);
    }

    private LocalDate writingDate(WritingEvaluation evaluation) {
        return evaluation.getAnswer()
                .getDailyItem()
                .getDailySet()
                .getLearningDate();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record WeightedScore(double score, double weight) {
    }
}
