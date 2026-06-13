package jp.co.translacat.domain.accountbook.chart.dto;

import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;

import java.math.BigDecimal;

public record AccountBookMonthlyTransactionAggregateDto(
        Integer month,
        AccountBookTransactionType type,
        BigDecimal amount
) {
}