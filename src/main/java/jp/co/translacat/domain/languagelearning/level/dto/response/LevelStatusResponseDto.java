package jp.co.translacat.domain.languagelearning.level.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;

public record LevelStatusResponseDto(
        LearningProfileState profileState,
        boolean initialLevelTestCompleted,
        boolean recheckRecommended,
        Long activeSessionId,
        Integer currentQuestionNumber,
        Double baseLevelScore
) {
}
