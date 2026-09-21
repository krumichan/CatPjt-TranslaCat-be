package jp.co.translacat.domain.accountbook.transaction.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReceiptConversionResponseDto(
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal originalAmount,
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
        boolean rateDateFallback,
        List<String> warnings) {
    public boolean registrable() {
        return "CONVERTED".equals(conversionStatus) || "NOT_REQUIRED".equals(conversionStatus);
    }
}
