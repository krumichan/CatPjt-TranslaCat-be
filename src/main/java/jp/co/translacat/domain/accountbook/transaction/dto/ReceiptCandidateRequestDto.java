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
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[^\\p{Cc}]+$") String categoryName,
        @Pattern(regexp = "EXISTING|DEFAULT|NEW|FALLBACK|USER") String categorySource,
        @Size(max = 160) @Pattern(regexp = "^[^\\p{Cc}]*$") String categoryReason,
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
        @Size(max = 20) String reviewStatus,
        @Pattern(regexp = "AUTOMATIC|ASSISTED") String reviewMode,
        @PositiveOrZero Integer draftRevision,
        @PositiveOrZero Integer reviewedRevision,
        @Size(min = 4, max = 4) List<@NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double> sourceRegion,
        Boolean branchOmittedByUser) {
    public ReceiptCandidateRequestDto(
            String receiptId, String title, String storeName, String branchName,
            String categoryName, BigDecimal purchaseTotal,
            List<ReceiptPaymentItemDto> paymentBreakdown, BigDecimal cashTendered,
            BigDecimal change, BigDecimal originalAmount, String originalCurrencyCode,
            LocalDate transactionDate, String memo, String conversionQuoteId,
            String sourceImageId, Integer analysisRevision, String amountPolicyVersion,
            String amountReason, String reviewStatus) {
        this(receiptId, title, storeName, branchName, categoryName, null, null, purchaseTotal,
                paymentBreakdown, cashTendered, change, originalAmount,
                originalCurrencyCode, transactionDate, null, memo, conversionQuoteId,
                sourceImageId, analysisRevision, amountPolicyVersion, amountReason,
                reviewStatus, null, null, null, null, null);
    }

    public ReceiptCandidateRequestDto(
            String receiptId, String title, String storeName, String branchName,
            String categoryName, BigDecimal purchaseTotal,
            List<ReceiptPaymentItemDto> paymentBreakdown, BigDecimal cashTendered,
            BigDecimal change, BigDecimal originalAmount, String originalCurrencyCode,
            LocalDate transactionDate, String transactionTime, String memo,
            String conversionQuoteId, String sourceImageId, Integer analysisRevision,
            String amountPolicyVersion, String amountReason, String reviewStatus) {
        this(receiptId, title, storeName, branchName, categoryName, null, null, purchaseTotal,
                paymentBreakdown, cashTendered, change, originalAmount,
                originalCurrencyCode, transactionDate, transactionTime, memo,
                conversionQuoteId, sourceImageId, analysisRevision, amountPolicyVersion,
                amountReason, reviewStatus, null, null, null, null, null);
    }

    public ReceiptCandidateRequestDto(
            String receiptId, String title, String storeName, String categoryName,
            BigDecimal originalAmount, String originalCurrencyCode, LocalDate transactionDate,
            String memo, String conversionQuoteId) {
        this(receiptId, title, storeName, null, categoryName, null, null, originalAmount, List.of(), null,
                null, originalAmount, originalCurrencyCode, transactionDate, null, memo,
                conversionQuoteId, receiptId, 1, "receipt-book-amount-v1",
                "PURCHASE_TOTAL_NO_PAYMENT_ALLOCATION", "READY",
                null, null, null, null, null);
    }
}
