package jp.co.translacat.domain.accountbook.transaction.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;
import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionSourceType;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
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
        String exchangeRateProvider,
        String targetCurrencyCode,
        Instant rateFetchedAt,
        Instant convertedAt,
        Integer roundingPrecision,
        String roundingMode,
        String conversionPolicyVersion,
        String conversionQuoteId,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal purchaseTotal,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal bookAmount,
        String receiptPaymentBreakdownJson,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal cashTendered,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal changeAmount,
        String amountPolicyVersion,
        String amountReason,
        String amountReviewStatus,
        String receiptBranchName,
        String receiptSourceImageId,
        Integer receiptAnalysisRevision,
        String receiptTransactionTime) {
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
                transaction.getExchangeRateProvider(),
                transaction.getTargetCurrencyCode(),
                transaction.getRateFetchedAt(),
                transaction.getConvertedAt(),
                transaction.getRoundingPrecision(),
                transaction.getRoundingMode(),
                transaction.getConversionPolicyVersion(),
                transaction.getConversionQuoteId(),
                transaction.getPurchaseTotal(),
                transaction.getBookAmount(),
                transaction.getReceiptPaymentBreakdownJson(),
                transaction.getCashTendered(),
                transaction.getChangeAmount(),
                transaction.getAmountPolicyVersion(),
                transaction.getAmountReason(),
                transaction.getAmountReviewStatus(),
                transaction.getReceiptBranchName(),
                transaction.getReceiptSourceImageId(),
                transaction.getReceiptAnalysisRevision(),
                transaction.getReceiptTransactionTime());
    }
}
