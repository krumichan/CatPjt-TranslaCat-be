package jp.co.translacat.domain.languagelearning.profile.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;

import java.time.LocalDate;
import java.util.List;

public record ProfileResponseDto(
        String profileVersion,
        LearningProfileState state,
        Double baseLevelScore,
        LocalDate calibrationStartedDate,
        LocalDate calibrationCompletedDate,
        SkillScoresResponseDto skillScores,
        DifficultyPerformanceResponseDto difficultyPerformance,
        double confidence,
        String trend,
        List<KeywordMasteryResponseDto> keywordMasteries,
        List<ProfileSignalResponseDto> grammarWeaknesses,
        List<ProfileSignalResponseDto> errorPatterns,
        List<ProfileSignalResponseDto> strengths,
        List<ProfileSignalResponseDto> weaknesses,
        List<ProfileSignalResponseDto> recommendedFocus
) {
}
