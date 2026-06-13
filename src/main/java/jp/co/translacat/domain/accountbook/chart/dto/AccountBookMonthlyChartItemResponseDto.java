package jp.co.translacat.domain.accountbook.chart.dto;

import java.math.BigDecimal;

public record AccountBookMonthlyChartItemResponseDto(
        Integer year,
        Integer month,
        BigDecimal incomeAmount,
        BigDecimal expenseAmount,
        BigDecimal balance,
        BigDecimal expenseGoalAmount
) {
}