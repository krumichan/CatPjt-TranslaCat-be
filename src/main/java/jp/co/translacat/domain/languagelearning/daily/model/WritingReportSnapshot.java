package jp.co.translacat.domain.languagelearning.daily.model;

import jp.co.translacat.domain.languagelearning.dashboard.dto.response.MonthlyReportResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.RecentLearningResponseDto;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.ScorePointResponseDto;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryItemResponseDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * LL이 계산한 Writing 보고서와 공통 기능 집계에 필요한 원본 평가 사실이다.
 */
public record WritingReportSnapshot(
        boolean started,
        int todayCompleted,
        int todayTotal,
        long totalStudySentenceCount,
        Double weeklyAverageScore,
        Double monthlyAverageScore,
        List<ScorePointResponseDto> metricTrend,
        List<RecentLearningResponseDto> recentLearningHistory,
        MonthlyReportResponseDto monthlyReport,
        List<LocalDate> completedDates,
        List<LearningHistoryItemResponseDto> history,
        List<Evaluation> evaluations
) {
    public record Evaluation(LocalDate learningDate, LocalDateTime evaluatedAt, Integer overallScore,
                             Integer meaningScore, Integer grammarScore, Integer vocabularyScore,
                             Integer naturalnessScore, Integer expressionScore, String profileSignalsJson) {
    }
}
