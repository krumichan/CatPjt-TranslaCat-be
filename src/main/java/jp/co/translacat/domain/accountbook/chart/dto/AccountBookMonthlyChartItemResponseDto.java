package jp.co.translacat.domain.accountbook.chart.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;

import java.math.BigDecimal;

public record AccountBookMonthlyChartItemResponseDto(
        Integer year,
        Integer month,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal incomeAmount,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal expenseAmount,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal balance,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal expenseGoalAmount) {
}
