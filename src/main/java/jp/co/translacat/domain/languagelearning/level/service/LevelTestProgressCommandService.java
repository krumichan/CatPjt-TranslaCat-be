package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestEvaluation;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestAdaptivePolicy;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestRecipe;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestScoringPolicy;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestEvaluationRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileCommandService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LevelTestProgressCommandService {

    private final LevelTestResponseRepository responseRepository;
    private final LevelTestEvaluationRepository evaluationRepository;
    private final LevelTestItemRepository itemRepository;
    private final LevelTestSessionRepository sessionRepository;
    private final LevelTestAdaptivePolicy adaptivePolicy;
    private final LevelTestScoringPolicy scoringPolicy;
    private final LearningProfileCommandService profileCommandService;
    private final LanguageLearningUserSettingQueryService settingQueryService;
    private final LearningActivityCommandService activityCommandService;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional
    public ProgressResult apply(
            Long responseId,
            EvaluationPayload payload
    ) {
        LevelTestResponse response = responseRepository
                .findById(responseId)
                .orElseThrow();
        LevelTestItem item = itemRepository
                .findLockedById(response.getItem().getId())
                .orElseThrow();
        var session = sessionRepository
                .findLockedById(item.getSession().getId())
                .orElseThrow();

        LevelTestEvaluation evaluation = evaluationRepository
                .findByResponseId(responseId)
                .orElse(null);
        if (evaluation != null
                && item.getStatus() == LevelTestItemStatus.EVALUATED) {
            return new ProgressResult(
                    evaluation.isEvaluable(),
                    evaluation.getScore(),
                    evaluation.getReasonCode(),
                    session.getStatus() == LevelTestSessionStatus.COMPLETED
            );
        }

        LocalDateTime now = LocalDateTime.now();
        if (evaluation == null) {
            evaluationRepository.save(LevelTestEvaluation.create(
                    response,
                    payload.evaluable(),
                    payload.score(),
                    payload.confidence(),
                    jsonCodec.write(payload.metrics()),
                    jsonCodec.write(payload.strengths()),
                    jsonCodec.write(payload.improvements()),
                    payload.transcript(),
                    jsonCodec.write(payload.recommendedAnswers()),
                    jsonCodec.write(payload.detailedFeedback()),
                    jsonCodec.write(payload.assessmentSignals()),
                    payload.reasonCode(),
                    payload.evaluationVersion(),
                    response.getManualEvaluationRetryCount(),
                    now
            ));
        } else {
            evaluation.replace(
                    payload.evaluable(),
                    payload.score(),
                    payload.confidence(),
                    jsonCodec.write(payload.metrics()),
                    jsonCodec.write(payload.strengths()),
                    jsonCodec.write(payload.improvements()),
                    payload.transcript(),
                    jsonCodec.write(payload.recommendedAnswers()),
                    jsonCodec.write(payload.detailedFeedback()),
                    jsonCodec.write(payload.assessmentSignals()),
                    payload.reasonCode(),
                    payload.evaluationVersion(),
                    response.getManualEvaluationRetryCount(),
                    now
            );
        }

        if (!payload.evaluable() || payload.score() == null) {
            item.markEvaluationFailed();
            session.resumeAfterEvaluation(now);
            return new ProgressResult(
                    false,
                    null,
                    payload.reasonCode(),
                    false
            );
        }

        item.markEvaluated();
        int nextBand = item.getAnswerMode() == LevelTestAnswerMode.CHOICE
                ? adaptivePolicy.afterObjective(
                        item.getComplexityBandValue(),
                        payload.score() == 100
                )
                : adaptivePolicy.afterEvaluated(
                        item.getComplexityBandValue(),
                        payload.score()
                );

        if (item.getQuestionNumber() < LevelTestRecipe.TOTAL_QUESTIONS) {
            session.resumeAfterEvaluation(now);
            session.advance(nextBand, now);
            return new ProgressResult(
                    true,
                    payload.score(),
                    null,
                    false
            );
        }

        Map<LevelTestDomain, Integer> domainScores =
                calculateDomainScores(session.getId());
        int overall = scoringPolicy.overall(domainScores);
        String proficiencyBand = scoringPolicy.band(overall);
        session.complete(overall, proficiencyBand, now);

        var setting = settingQueryService.getOrCreateEntity(
                session.getUser().getId()
        );
        LocalDate completedDate = LocalDate.now(
                resolveZone(setting.getTimezone())
        );
        profileCommandService.completeLevelTest(
                session.getUser().getId(),
                overall,
                completedDate
        );

        long durationSeconds = Math.max(
                0,
                Duration.between(session.getStartedAt(), now).toSeconds()
        );
        activityCommandService.getOrCreate(
                session.getUser().getId(),
                LearningSource.LEVEL_TEST,
                String.valueOf(session.getId()),
                completedDate,
                "Language Level Test",
                durationSeconds,
                session.getStartedAt(),
                now
        );
        return new ProgressResult(
                true,
                payload.score(),
                null,
                true
        );
    }

    @Transactional(readOnly = true)
    public Map<LevelTestDomain, Integer> calculateDomainScores(
            Long sessionId
    ) {
        List<LevelTestEvaluation> evaluations = evaluationRepository
                .findAllByResponseItemSessionIdOrderByResponseItemQuestionNumberAsc(
                        sessionId
                );
        if (evaluations.size() != LevelTestRecipe.TOTAL_QUESTIONS
                || evaluations.stream().anyMatch(
                        value -> !value.isEvaluable()
                                || value.getScore() == null
                )) {
            throw new BusinessException(
                    "Level Test의 20문항이 모두 평가되지 않았습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }

        Map<LevelTestDomain, List<Integer>> grouped =
                new EnumMap<>(LevelTestDomain.class);
        for (LevelTestDomain domain : LevelTestDomain.values()) {
            grouped.put(domain, new ArrayList<>());
        }
        for (LevelTestEvaluation evaluation : evaluations) {
            grouped.get(evaluation.getResponse().getItem().getDomain())
                    .add(evaluation.getScore());
        }

        Map<LevelTestDomain, Integer> result =
                new EnumMap<>(LevelTestDomain.class);
        grouped.forEach((domain, scores) -> result.put(
                domain,
                scoringPolicy.domainScore(scores)
        ));
        return result;
    }

    private ZoneId resolveZone(String timezone) {
        try {
            return ZoneId.of(
                    timezone == null ? "Asia/Tokyo" : timezone
            );
        } catch (Exception exception) {
            return ZoneId.of("Asia/Tokyo");
        }
    }

    public record EvaluationPayload(
            boolean evaluable,
            Integer score,
            Double confidence,
            List<Map<String, Object>> metrics,
            List<String> strengths,
            List<String> improvements,
            String transcript,
            List<String> recommendedAnswers,
            List<Map<String, Object>> detailedFeedback,
            List<Map<String, Object>> assessmentSignals,
            String reasonCode,
            String evaluationVersion
    ) {
        public EvaluationPayload {
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            improvements = improvements == null
                    ? List.of()
                    : List.copyOf(improvements);
            recommendedAnswers = recommendedAnswers == null
                    ? List.of()
                    : List.copyOf(recommendedAnswers);
            detailedFeedback = detailedFeedback == null
                    ? List.of()
                    : List.copyOf(detailedFeedback);
            assessmentSignals = assessmentSignals == null
                    ? List.of()
                    : List.copyOf(assessmentSignals);
        }
    }

    public record ProgressResult(
            boolean evaluable,
            Integer score,
            String reasonCode,
            boolean completed
    ) {
    }
}
