package jp.co.translacat.domain.accountbook.chart.dto;

import java.math.BigDecimal;

public record AccountBookRankingChartItemResponseDto(
        String name,
        BigDecimal amount,
        Long transactionCount,
        BigDecimal percentage
) {
}