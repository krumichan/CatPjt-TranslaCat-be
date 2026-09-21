package jp.co.translacat.domain.accountbook.transaction.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;
import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionSourceType;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AccountBookTransactionResponseDto(
        Long id,
        Long accountBookId,
        AccountBookTransactionType type,
        String title,
        String storeName,
        String category,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal amount,
        LocalDate transactionDate,
        String memo,
        LocalDateTime createdAt,
        AccountBookTransactionSourceType sourceType,
        Long sourceId,
        Integer sourceYear,
        Integer sourceMonth,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal originalAmount,
        String originalCurrencyCode,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal exchangeRate,
        LocalDate requestedRateDate,
        LocalDate effectiveRateDate,
        String exchangeRateProvider) {
    public static AccountBookTransactionResponseDto from(AccountBookTransaction transaction) {
        return new AccountBookTransactionResponseDto(
                transaction.getId(),
                transaction.getAccountBook().getId(),
                transaction.getType(),
                transaction.getTitle(),
                transaction.getStoreName(),
                transaction.getCategory(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getMemo(),
                transaction.getCreatedAt(),
                transaction.getSourceType(),
                transaction.getSourceId(),
                transaction.getSourceYear(),
                transaction.getSourceMonth(),
                transaction.getOriginalAmount(),
                transaction.getOriginalCurrencyCode(),
                transaction.getExchangeRate(),
                transaction.getRequestedRateDate(),
                transaction.getEffectiveRateDate(),
                transaction.getExchangeRateProvider());
    }
}
