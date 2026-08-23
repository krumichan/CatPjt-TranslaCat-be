package jp.co.translacat.domain.languagelearning.listening.dashboard.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningRecommendationStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningWeaknessState;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.evaluation.entity.ListeningTaskEvaluation;
import jp.co.translacat.domain.languagelearning.listening.evaluation.repository.ListeningTaskEvaluationRepository;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningProfilePolicy;
import jp.co.translacat.domain.languagelearning.listening.profile.entity.ListeningMetricHistory;
import jp.co.translacat.domain.languagelearning.listening.profile.repository.ListeningMetricHistoryRepository;
import jp.co.translacat.domain.languagelearning.listening.recommendation.entity.LearningRecommendation;
import jp.co.translacat.domain.languagelearning.listening.recommendation.repository.LearningRecommendationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListeningDashboardQueryService {

    private final ListeningMetricHistoryRepository historyRepository;
    private final LearningRecommendationRepository recommendationRepository;
    private final ListeningProfilePolicy profilePolicy;
    private final ListeningTaskEvaluationRepository evaluationRepository;

    public List<ListeningApiContract.MetricProfileView> profiles(
            Long userId,
            String language,
            ListeningTaskType taskType
    ) {
        List<ListeningApiContract.MetricProfileView> values = new ArrayList<>();

        for (ListeningProfileMetric metric : ListeningProfileMetric.values()) {
            List<ListeningMetricHistory> source = taskType == null
                    ? historyRepository
                            .findTop30ByUserIdAndLearningLanguageAndMetricTypeAndProfileAppliedTrueOrderByCreatedAtDesc(
                                    userId, language, metric)
                    : historyRepository
                            .findTop30ByUserIdAndLearningLanguageAndTaskTypeAndMetricTypeAndProfileAppliedTrueOrderByCreatedAtDesc(
                                    userId, language, taskType, metric);
            List<ListeningProfilePolicy.Signal> signals = source.stream()
                    .map(value -> new ListeningProfilePolicy.Signal(
                            value.getReferenceActivityId(),
                            value.getRawScore(),
                            value.getConfidence(),
                            value.getAssistanceLevel(),
                            value.getEvidenceWeight(),
                            value.isOfficial(),
                            value.isPractice(),
                            !value.isProfileApplied(),
                            value.getCreatedAt()
                    )).toList();
            var aggregate = profilePolicy.aggregate(signals);
            var weakness = profilePolicy.weakness(signals);
            var growth = profilePolicy.growth(
                    signals,
                    weakness.state() == ListeningWeaknessState.ACTIVE
                            || weakness.state() == ListeningWeaknessState.IMPROVING
            );
            values.add(new ListeningApiContract.MetricProfileView(
                    metric,
                    aggregate.score(),
                    aggregate.sampleCount(),
                    aggregate.confidence(),
                    weakness.state(),
                    growth.active(),
                    growth.delta()
            ));
        }

        return List.copyOf(values);
    }

    public List<ListeningApiContract.TaskTrendView> trends(
            Long userId,
            String learningLanguage,
            LocalDate from,
            LocalDate to,
            ListeningTaskType taskType
    ) {
        List<ListeningTaskEvaluation> source = evaluationRepository
                .findOfficialTrendSource(
                        userId,
                        learningLanguage,
                        from.atStartOfDay(),
                        to.atTime(java.time.LocalTime.MAX)
                ).stream()
                .filter(value -> taskType == null
                        || value.getTaskType() == taskType)
                .toList();
        record Key(ListeningTaskType task, LocalDate date) {
        }

        Map<Key, List<Double>> grouped = new LinkedHashMap<>();
        source.forEach(value -> grouped.computeIfAbsent(
                new Key(value.getTaskType(), value.getEvaluatedAt().toLocalDate()),
                ignored -> new ArrayList<>()
        ).add(value.getScore()));

        return grouped.entrySet().stream()
                .map(entry -> new ListeningApiContract.TaskTrendView(
                        entry.getKey().task(),
                        entry.getKey().date(),
                        entry.getValue().stream()
                                .mapToDouble(Double::doubleValue)
                                .average().stream().boxed()
                                .findFirst().orElse(null),
                        entry.getValue().size()
                ))
                .sorted(Comparator.comparing(
                        ListeningApiContract.TaskTrendView::date
                ).thenComparing(value -> value.taskType().ordinal()))
                .toList();
    }

    public List<ListeningApiContract.RecommendationView> recommendations(
            Long userId,
            String learningLanguage
    ) {
        return recommendationRepository
                .findTop2ByUserIdAndLearningLanguageAndStatusOrderByPriorityAscCreatedAtDesc(
                        userId,
                        learningLanguage,
                        ListeningRecommendationStatus.ACTIVE
                ).stream()
                .map(this::recommendation)
                .toList();
    }

    private ListeningApiContract.RecommendationView recommendation(
            LearningRecommendation value
    ) {
        return new ListeningApiContract.RecommendationView(
                value.getId(),
                value.getTargetMetric(),
                value.getRecommendedActivity(),
                value.getRecommendedTask(),
                value.getReason(),
                value.getCtaLabel(),
                value.getPriority(),
                value.getStatus(),
                value.getExpiresAt()
        );
    }
}
