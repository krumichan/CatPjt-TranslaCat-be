package jp.co.translacat.domain.accountbook.chart.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountBookMonthlyChartResponseDto(
        Integer year,
        List<AccountBookMonthlyChartItemResponseDto> months
) {
}