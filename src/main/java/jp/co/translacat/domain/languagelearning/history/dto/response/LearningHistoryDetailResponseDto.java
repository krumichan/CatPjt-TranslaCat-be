package jp.co.translacat.domain.languagelearning.history.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;

public record LearningHistoryDetailResponseDto(
        String activityId,
        LearningSource source,
        Object detail
) {
}
