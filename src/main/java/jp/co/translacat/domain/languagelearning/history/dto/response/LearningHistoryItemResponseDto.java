package jp.co.translacat.domain.languagelearning.history.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;

import java.time.LocalDate;

public record LearningHistoryItemResponseDto(
        String activityId,
        LearningSource source,
        LocalDate learningDate,
        String title,
        String topic,
        long durationSeconds,
        Double overallScore,
        String completionStatus,
        String evaluationStatus
) {
}
