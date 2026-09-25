package jp.co.translacat.domain.accountbook.chart.dto;

import java.util.List;

public record AccountBookMonthlyChartResponseDto(
        Integer year,
        List<AccountBookMonthlyChartItemResponseDto> months
) {
}
