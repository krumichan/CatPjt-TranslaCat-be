package jp.co.translacat.domain.accountbook.chart.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountBookRankingChartResponseDto(
        Integer year,
        Integer month,
        BigDecimal totalAmount,
        List<AccountBookRankingChartItemResponseDto> items
) {
}