package jp.co.translacat.domain.languagelearning.growth.model;

import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity가 아닌 LL 응답이다. Core에서 이 객체의 점수를 갱신하지 않는다.
 */
public record GrowthProfileSnapshot(
        String profileVersion, LearningProfileState state, Double baseLevelScore,
        LocalDate calibrationStartedDate, LocalDate calibrationCompletedDate,
        Double meaningScore, Double grammarScore, Double vocabularyScore, Double naturalnessScore,
        Double expressionScore,
        Double reviewPerformance, Double normalPerformance, Double challengePerformance,
        int evaluationCount, double confidence, String trend, String additionalSignalsJson,
        String baselineCompletionId, LocalDateTime baselineCompletedAt
) {
}
