package jp.co.translacat.domain.accountbook.transaction.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReceiptAnalysisResponseDto(
        List<Item> receipts,
        int receiptCount,
        List<String> warnings,
        String ocrEngine,
        Boolean usedAi) {
    public record Item(
            String receiptId,
            String title,
            String storeName,
            String branchName,
            @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal purchaseTotal,
            List<ReceiptPaymentItemDto> paymentBreakdown,
            @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal cashTendered,
            @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal change,
            @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal bookAmount,
            String amountPolicyVersion,
            String amountReason,
            String reviewStatus,
            @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal originalAmount,
            String detectedCurrencyCode,
            LocalDate transactionDate,
            String transactionTime,
            String categoryName,
            String memo,
            Double confidence,
            String detectedLanguage,
            String status,
            List<String> warnings,
            String originalCurrencyCode,
            String accountBookCurrencyCode,
            @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal convertedAmount,
            @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal exchangeRate,
            LocalDate requestedRateDate,
            LocalDate effectiveRateDate,
            String exchangeRateProvider,
            Instant rateFetchedAt,
            Instant convertedAt,
            int roundingPrecision,
            String roundingMode,
            String conversionPolicyVersion,
            String conversionQuoteId,
            String conversionStatus,
            boolean rateDateFallback) {}
}
