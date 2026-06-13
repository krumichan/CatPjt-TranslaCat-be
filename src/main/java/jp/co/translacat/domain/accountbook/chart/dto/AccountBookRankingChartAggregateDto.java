package jp.co.translacat.domain.accountbook.chart.dto;

import java.math.BigDecimal;

public record AccountBookRankingChartAggregateDto(
        String name,
        BigDecimal amount,
        Long transactionCount
) {
}