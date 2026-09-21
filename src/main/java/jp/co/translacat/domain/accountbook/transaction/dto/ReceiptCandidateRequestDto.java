package jp.co.translacat.domain.accountbook.transaction.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Source facts only. Rates and converted amounts supplied by clients are never authoritative. */
public record ReceiptCandidateRequestDto(
        @NotBlank @Size(max = 100) String receiptId,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 100) String storeName,
        @Size(max = 100) String branchName,
        @NotBlank @Size(max = 50) String categoryName,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 20, fraction = 8)
                BigDecimal purchaseTotal,
        @Size(max = 30) List<@Valid ReceiptPaymentItemDto> paymentBreakdown,
        @DecimalMin(value = "0") @Digits(integer = 20, fraction = 8) BigDecimal cashTendered,
        @DecimalMin(value = "0") @Digits(integer = 20, fraction = 8) BigDecimal change,
        @DecimalMin(value = "0") @Digits(integer = 20, fraction = 8) BigDecimal originalAmount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String originalCurrencyCode,
        @NotNull @PastOrPresent LocalDate transactionDate,
        @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?") String transactionTime,
        @Size(max = 500) String memo,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String conversionQuoteId,
        @NotBlank @Size(max = 100) String sourceImageId,
        @NotNull @Positive Integer analysisRevision,
        @Size(max = 40) String amountPolicyVersion,
        @Size(max = 100) String amountReason,
        @Size(max = 20) String reviewStatus) {
    public ReceiptCandidateRequestDto(
            String receiptId, String title, String storeName, String branchName,
            String categoryName, BigDecimal purchaseTotal,
            List<ReceiptPaymentItemDto> paymentBreakdown, BigDecimal cashTendered,
            BigDecimal change, BigDecimal originalAmount, String originalCurrencyCode,
            LocalDate transactionDate, String memo, String conversionQuoteId,
            String sourceImageId, Integer analysisRevision, String amountPolicyVersion,
            String amountReason, String reviewStatus) {
        this(receiptId, title, storeName, branchName, categoryName, purchaseTotal,
                paymentBreakdown, cashTendered, change, originalAmount,
                originalCurrencyCode, transactionDate, null, memo, conversionQuoteId,
                sourceImageId, analysisRevision, amountPolicyVersion, amountReason,
                reviewStatus);
    }

    public ReceiptCandidateRequestDto(
            String receiptId, String title, String storeName, String categoryName,
            BigDecimal originalAmount, String originalCurrencyCode, LocalDate transactionDate,
            String memo, String conversionQuoteId) {
        this(receiptId, title, storeName, null, categoryName, originalAmount, List.of(), null,
                null, originalAmount, originalCurrencyCode, transactionDate, null, memo,
                conversionQuoteId, receiptId, 1, "receipt-book-amount-v1",
                "PURCHASE_TOTAL_NO_PAYMENT_ALLOCATION", "READY");
    }
}
