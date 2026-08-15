package jp.co.translacat.domain.languagelearning.profile.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.ProfileSignalsDto;
import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.profile.dto.response.UnifiedProfileInsightResponseDto;
import jp.co.translacat.domain.languagelearning.profile.policy.LearningProfileAggregationWeightPolicy;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingEvaluationEligibilityDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingProfileSignalDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentLearningProfileInsightQueryService {

    private final WritingEvaluationRepository writingEvaluationRepository;
    private final SpeakingEvaluationRepository speakingEvaluationRepository;
    private final SpeakingTurnRepository speakingTurnRepository;
    private final LanguageLearningJsonCodec jsonCodec;
    private final LearningProfileAggregationWeightPolicy weightPolicy;

    public List<UnifiedProfileInsightResponseDto> getInsights(
            Long userId,
            LearningSource source,
            int limit
    ) {
        List<ActivitySignals> activities = loadActivities(userId).stream()
                .sorted(Comparator.comparing(ActivitySignals::evaluatedAt).reversed())
                .limit(LearningProfileAggregationWeightPolicy.MAX_EVALUATED_ACTIVITIES)
                .toList();

        Map<String, Aggregated> values = new LinkedHashMap<>();
        for (int index = 0; index < activities.size(); index++) {
            ActivitySignals activity = activities.get(index);
            if (source != null && activity.source() != source) {
                continue;
            }
            double recency = activity.source() == LearningSource.WRITING
                    ? weightPolicy.writingActivityWeight(index, activities.size())
                    : weightPolicy.recencyWeight(index, activities.size());
            for (Signal signal : activity.signals()) {
                double weighted = activity.source() == LearningSource.SPEAKING
                        ? speakingSignalWeight(activity, signal, index, activities.size())
                        : recency;
                add(values, activity.source(), signal, weighted);
            }
        }

        return values.values().stream()
                .filter(Aggregated::isDisplayable)
                .sorted(Comparator.comparingDouble(Aggregated::weightedEvidence).reversed())
                .limit(Math.max(1, limit))
                .map(Aggregated::toResponse)
                .toList();
    }

    public List<String> getSpeakingRecommendedFocus(Long userId, int limit) {
        return getInsights(userId, LearningSource.SPEAKING, 30).stream()
                .map(UnifiedProfileInsightResponseDto::recommendedFocus)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(Math.max(1, limit))
                .toList();
    }

    private List<ActivitySignals> loadActivities(Long userId) {
        List<ActivitySignals> result = new ArrayList<>();
        writingEvaluationRepository
                .findAllByUserIdAndContextAndStatusOrderByEvaluatedAtDesc(
                        userId,
                        WritingEvaluationContext.DAILY,
                        EvaluationStatus.SUCCESS
                )
                .forEach(evaluation -> result.add(toWritingActivity(evaluation)));
        speakingEvaluationRepository
                .findAllBySessionUserIdOrderByEvaluatedAtDesc(userId)
                .stream()
                .filter(evaluation -> "EVALUATED".equalsIgnoreCase(evaluation.getStatus()))
                .filter(evaluation -> evaluation.getOverallScore() != null)
                .forEach(evaluation -> result.add(toSpeakingActivity(evaluation)));
        return result;
    }

    private ActivitySignals toWritingActivity(WritingEvaluation evaluation) {
        ProfileSignalsDto signals = readWritingSignals(evaluation.getProfileSignalsJson());
        List<Signal> result = new ArrayList<>();
        addWriting(result, signals == null ? null : signals.strengthTags(), "STRENGTH");
        addWriting(result, signals == null ? null : signals.weaknessTags(), "WEAKNESS");
        addWriting(result, signals == null ? null : signals.grammarPatterns(), "WEAKNESS");
        addWriting(result, signals == null ? null : signals.vocabularyPatterns(), "WEAKNESS");
        addWriting(result, signals == null ? null : signals.naturalnessPatterns(), "WEAKNESS");
        addWriting(result, signals == null ? null : signals.expressionPatterns(), "WEAKNESS");
        addWriting(result, signals == null ? null : signals.meaningPatterns(), "WEAKNESS");
        return new ActivitySignals(
                LearningSource.WRITING,
                evaluation.getEvaluatedAt(),
                1.0,
                1.0,
                List.of(),
                deduplicate(result)
        );
    }

    private ActivitySignals toSpeakingActivity(SpeakingEvaluation evaluation) {
        List<AiSpeakingProfileSignalDto> signals = jsonCodec.read(
                evaluation.getProfileSignalsJson(),
                new TypeReference<List<AiSpeakingProfileSignalDto>>() {
                }
        );
        List<SpeakingTurn> turns = speakingTurnRepository
                .findAllBySessionIdOrderByTurnIndexAsc(evaluation.getSession().getId());
        List<AssistanceType> assistance = turns.stream()
                .flatMap(turn -> readAssistance(turn).stream())
                .distinct()
                .toList();
        AiSpeakingEvaluationEligibilityDto eligibility = readEligibility(evaluation);
        double validity = eligibility == null
                ? 1.0
                : eligibility.validSttTurnRatio();
        List<Signal> result = safe(signals).stream()
                .filter(signal -> signal != null
                        && signal.patternKey() != null
                        && !signal.patternKey().isBlank())
                .map(signal -> new Signal(
                        signal.patternKey().trim(),
                        normalizeDirection(signal.direction()),
                        signal.recommendedFocus(),
                        signal.confidence(),
                        signal.metricType()
                ))
                .toList();
        return new ActivitySignals(
                LearningSource.SPEAKING,
                evaluation.getEvaluatedAt(),
                evaluation.getEvaluationConfidence() == null
                        ? 0.0
                        : evaluation.getEvaluationConfidence(),
                validity,
                assistance,
                deduplicate(result)
        );
    }

    private double speakingSignalWeight(
            ActivitySignals activity,
            Signal signal,
            int index,
            int total
    ) {
        double base = weightPolicy.speakingActivityWeight(
                index,
                total,
                activity.confidence(),
                activity.validity(),
                activity.assistance(),
                signal.metricType()
        );
        return round(base * Math.max(0.0, Math.min(1.0, signal.confidence())));
    }

    private void add(
            Map<String, Aggregated> values,
            LearningSource source,
            Signal signal,
            double weight
    ) {
        String key = signal.direction() + ":" + signal.patternKey().toLowerCase();
        Aggregated aggregated = values.computeIfAbsent(
                key,
                ignored -> new Aggregated(signal.patternKey(), signal.direction())
        );
        aggregated.add(source, weight, signal.recommendedFocus());
    }

    private List<Signal> deduplicate(List<Signal> values) {
        Map<String, Signal> unique = new LinkedHashMap<>();
        for (Signal value : values) {
            unique.putIfAbsent(
                    value.direction() + ":" + value.patternKey().toLowerCase(),
                    value
            );
        }
        return List.copyOf(unique.values());
    }

    private void addWriting(
            List<Signal> result,
            List<String> values,
            String direction
    ) {
        for (String value : safe(values)) {
            if (value != null && !value.isBlank()) {
                result.add(new Signal(value.trim(), direction, null, 1.0, null));
            }
        }
    }

    private ProfileSignalsDto readWritingSignals(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return null;
        }
        return jsonCodec.read(json, ProfileSignalsDto.class);
    }

    private AiSpeakingEvaluationEligibilityDto readEligibility(
            SpeakingEvaluation evaluation
    ) {
        if (evaluation.getEligibilityJson() == null
                || evaluation.getEligibilityJson().isBlank()
                || evaluation.getEligibilityJson().equals("{}")) {
            return null;
        }
        return jsonCodec.read(
                evaluation.getEligibilityJson(),
                AiSpeakingEvaluationEligibilityDto.class
        );
    }

    private List<AssistanceType> readAssistance(SpeakingTurn turn) {
        if (turn.getAssistanceUsageJson() == null
                || turn.getAssistanceUsageJson().isBlank()) {
            return List.of();
        }
        return jsonCodec.read(
                turn.getAssistanceUsageJson(),
                new TypeReference<List<AssistanceType>>() {
                }
        );
    }

    private String normalizeDirection(String value) {
        return value == null || value.isBlank()
                ? "WEAKNESS"
                : value.trim().toUpperCase();
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ActivitySignals(
            LearningSource source,
            LocalDateTime evaluatedAt,
            double confidence,
            double validity,
            List<AssistanceType> assistance,
            List<Signal> signals
    ) {
    }

    private record Signal(
            String patternKey,
            String direction,
            String recommendedFocus,
            double confidence,
            jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingMetricType metricType
    ) {
    }

    private final class Aggregated {
        private final String patternKey;
        private final String direction;
        private final Map<LearningSource, Integer> evidenceBySource =
                new EnumMap<>(LearningSource.class);
        private final Set<LearningSource> sources = new LinkedHashSet<>();
        private int evidenceCount;
        private double weightedEvidence;
        private String recommendedFocus;

        private Aggregated(String patternKey, String direction) {
            this.patternKey = patternKey;
            this.direction = direction;
        }

        private void add(
                LearningSource source,
                double weight,
                String focus
        ) {
            evidenceCount++;
            weightedEvidence += Math.max(0.0, weight);
            sources.add(source);
            evidenceBySource.merge(source, 1, Integer::sum);
            if (focus != null && !focus.isBlank()) {
                recommendedFocus = focus;
            }
        }

        private boolean isDisplayable() {
            boolean sourceEstablished = evidenceBySource.values().stream()
                    .anyMatch(weightPolicy::isSourceEstablished);
            return sourceEstablished
                    || weightPolicy.isUnified(sources.size(), evidenceCount);
        }

        private double weightedEvidence() {
            return weightedEvidence;
        }

        private UnifiedProfileInsightResponseDto toResponse() {
            return new UnifiedProfileInsightResponseDto(
                    patternKey,
                    direction,
                    evidenceCount,
                    Math.round(weightedEvidence * 100.0) / 100.0,
                    new ArrayList<>(sources),
                    weightPolicy.isUnified(sources.size(), evidenceCount),
                    recommendedFocus
            );
        }
    }
}
