package jp.co.translacat.domain.languagelearning.level.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAssessmentVersion;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;

import java.time.LocalDateTime;

public record LevelTestHistoryItemResponseDto(
        Long sessionId,
        LevelTestAssessmentVersion assessmentVersion,
        LevelTestSessionType sessionType,
        Integer overallScore,
        String proficiencyBand,
        LevelTestDomainScoresResponseDto domainScores,
        LocalDateTime completedAt
) {
}
