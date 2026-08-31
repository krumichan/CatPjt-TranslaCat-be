package jp.co.translacat.domain.languagelearning.level.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAssessmentVersion;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;

import java.time.LocalDateTime;

public record LevelTestResultResponseDto(
        Long sessionId,
        LevelTestAssessmentVersion assessmentVersion,
        LevelTestSessionType sessionType,
        Integer overallScore,
        String proficiencyBand,
        LevelTestDomainScoresResponseDto domainScores,
        String recommendedDifficulty,
        LocalDateTime completedAt
) {
}
