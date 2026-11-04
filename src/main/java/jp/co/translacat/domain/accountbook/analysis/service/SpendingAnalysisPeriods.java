package jp.co.translacat.domain.accountbook.analysis.service;

import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.Request;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.PeriodType;
import java.time.LocalDate;

public final class SpendingAnalysisPeriods {
    private SpendingAnalysisPeriods() { }
    public record Range(LocalDate start, LocalDate end) { }

    public static Range resolve(Request request) {
        // 사용자 지정 기간은 끝 날짜를 포함하며 최소 한 달은 달력 기준으로 계산한다.
        if (request.periodType() == null || request.year() == null || request.year() < 2000 || request.year() > 9998) {
            throw new IllegalArgumentException("Invalid analysis year or period");
        }
        if ((request.periodType() != PeriodType.QUARTERLY && request.quarter() != null)
                || (request.periodType() != PeriodType.HALF_YEARLY && request.half() != null)
                || (request.periodType() != PeriodType.MONTHLY && request.month() != null)
                || (request.periodType() != PeriodType.CUSTOM && (request.startDate() != null || request.endDate() != null))) {
            throw new IllegalArgumentException("Unexpected period fields");
        }
        if (request.periodType() == PeriodType.CUSTOM) {
            var start = request.startDate();
            var last = request.endDate();
            if (start == null || last == null || start.getYear() != request.year() || last.getYear() > 9998
                    || last.plusDays(1).isBefore(start.plusMonths(1))) {
                throw new IllegalArgumentException("Custom analysis requires at least one calendar month");
            }
            return new Range(start, last.plusDays(1));
        }

        // 나머지는 달력의 월·분기·반기·연도로 정규화한다.
        int startMonth = 1;
        int months = 12;
        if (request.periodType() == PeriodType.MONTHLY) {
            if (request.month() == null || request.month() < 1 || request.month() > 12) throw new IllegalArgumentException("Invalid month");
            startMonth = request.month();
            months = 1;
        } else if (request.periodType() == PeriodType.QUARTERLY) {
            if (request.quarter() == null || request.quarter() < 1 || request.quarter() > 4) throw new IllegalArgumentException("Invalid quarter");
            startMonth = (request.quarter() - 1) * 3 + 1;
            months = 3;
        } else if (request.periodType() == PeriodType.HALF_YEARLY) {
            if (request.half() == null || request.half() < 1 || request.half() > 2) throw new IllegalArgumentException("Invalid half");
            startMonth = (request.half() - 1) * 6 + 1;
            months = 6;
        }
        var start = LocalDate.of(request.year(), startMonth, 1);
        return new Range(start, start.plusMonths(months));
    }
}
