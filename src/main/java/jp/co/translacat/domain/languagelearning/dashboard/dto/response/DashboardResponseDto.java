package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

import jp.co.translacat.domain.languagelearning.profile.dto.response.DifficultyPerformanceResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.KeywordMasteryResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.ProfileSignalResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.SkillScoresResponseDto;

import java.util.List;

public record DashboardResponseDto(
        int todayCompleted,
        int todayTotal,
        int currentStreak,
        long totalStudySentenceCount,
        Double weeklyAverageScore,
        Double monthlyAverageScore,
        SkillScoresResponseDto skillRadar,
        List<ScorePointResponseDto> metricTrend,
        DifficultyPerformanceResponseDto difficultyPerformance,
        List<KeywordMasteryResponseDto> keywordMastery,
        List<ProfileSignalResponseDto> grammarWeaknesses,
        List<ProfileSignalResponseDto> errorPatterns,
        List<RecentLearningResponseDto> recentLearningHistory,
        MonthlyReportResponseDto monthlyReport
) {
}
