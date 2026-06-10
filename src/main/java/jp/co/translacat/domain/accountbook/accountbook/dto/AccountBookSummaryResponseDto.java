package jp.co.translacat.domain.accountbook.accountbook.dto;

import java.math.BigDecimal;

public record AccountBookSummaryResponseDto(
        Long accountBookId,
        String currencyCode,
        BigDecimal incomeAmount,
        BigDecimal expenseAmount,
        BigDecimal balance,
        Long transactionCount
) {
    public AccountBookSummaryResponseDto {
        incomeAmount = incomeAmount == null ? BigDecimal.ZERO : incomeAmount;
        expenseAmount = expenseAmount == null ? BigDecimal.ZERO : expenseAmount;
        balance = balance == null ? BigDecimal.ZERO : balance;
        transactionCount = transactionCount == null ? 0L : transactionCount;
    }
}