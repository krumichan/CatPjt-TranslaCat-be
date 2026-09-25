package jp.co.translacat.domain.accountbook.transaction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Recalculation also works before the user has supplied a category or title.
 */
public record ReceiptConversionRequestDto(
        @Digits(integer = 20, fraction = 8) BigDecimal originalAmount,
        @Size(max = 3) String originalCurrencyCode,
        LocalDate transactionDate,
        @Digits(integer = 20, fraction = 8) BigDecimal purchaseTotal,
        @Size(max = 30) List<@Valid ReceiptPaymentItemDto> paymentBreakdown,
        @Digits(integer = 20, fraction = 8) BigDecimal cashTendered,
        @Digits(integer = 20, fraction = 8) BigDecimal change) {
    public ReceiptConversionRequestDto(
            BigDecimal originalAmount, String originalCurrencyCode, LocalDate transactionDate) {
        this(originalAmount, originalCurrencyCode, transactionDate, originalAmount, List.of(), null, null);
    }
}
