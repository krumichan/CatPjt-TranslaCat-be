package jp.co.translacat.domain.languagelearning.level.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAssessmentVersion;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;

import java.time.LocalDateTime;

public record LevelSessionResponseDto(
        Long sessionId,
        LevelTestSessionType sessionType,
        LevelTestAssessmentVersion assessmentVersion,
        LevelTestSessionStatus status,
        int totalQuestions,
        int currentQuestionNumber,
        int currentComplexityBand,
        Double baseLevelScore,
        String proficiencyBand,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
