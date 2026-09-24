package jp.co.translacat.domain.accountbook.accountbook.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;

import java.math.BigDecimal;

public record AccountBookSummaryResponseDto(
        Long accountBookId,
        String currencyCode,
        Integer currencyDecimalPlaces,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal incomeAmount,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal expenseAmount,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal balance,
        Long transactionCount) {
    public AccountBookSummaryResponseDto {
        incomeAmount = incomeAmount == null ? BigDecimal.ZERO : incomeAmount;
        expenseAmount = expenseAmount == null ? BigDecimal.ZERO : expenseAmount;
        balance = balance == null ? BigDecimal.ZERO : balance;
        transactionCount = transactionCount == null ? 0L : transactionCount;
    }
}
