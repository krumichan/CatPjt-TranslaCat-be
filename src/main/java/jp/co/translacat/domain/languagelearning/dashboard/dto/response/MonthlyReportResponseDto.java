package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

import java.time.YearMonth;

public record MonthlyReportResponseDto(
        YearMonth month,
        long evaluatedSentenceCount,
        Double overallAverage,
        String strongestMetric,
        String weakestMetric
) {
}
