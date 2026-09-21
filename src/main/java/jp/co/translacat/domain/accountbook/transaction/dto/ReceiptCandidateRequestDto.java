package jp.co.translacat.domain.accountbook.transaction.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Source facts only. Rates and converted amounts supplied by clients are never authoritative. */
public record ReceiptCandidateRequestDto(
        @NotBlank @Size(max = 100) String receiptId,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 100) String storeName,
        @NotBlank @Size(max = 50) String categoryName,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 20, fraction = 8)
                BigDecimal originalAmount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String originalCurrencyCode,
        @NotNull @PastOrPresent LocalDate transactionDate,
        @Size(max = 500) String memo) {}
