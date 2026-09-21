package jp.co.translacat.domain.accountbook.transaction.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;

import java.math.BigDecimal;
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
            @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal originalAmount,
            String detectedCurrencyCode,
            LocalDate transactionDate,
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
            String conversionStatus,
            boolean rateDateFallback) {}
}
