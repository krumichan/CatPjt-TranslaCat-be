package jp.co.translacat.domain.accountbook.transaction.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Recalculation also works before the user has supplied a category or title. */
public record ReceiptConversionRequestDto(
        @Digits(integer = 20, fraction = 8) BigDecimal originalAmount,
        @Size(max = 3) String originalCurrencyCode,
        LocalDate transactionDate) {}
