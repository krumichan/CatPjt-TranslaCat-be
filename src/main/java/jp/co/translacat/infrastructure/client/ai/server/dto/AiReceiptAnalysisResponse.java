package jp.co.translacat.infrastructure.client.ai.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiReceiptAnalysisResponse(
        List<Item> receipts,
        @JsonProperty("receipt_count") Integer receiptCount,
        List<String> warnings,
        @JsonProperty("ocr_engine") String ocrEngine,
        @JsonProperty("used_ai") Boolean usedAi,
        @JsonProperty("analysis_trace_id") String analysisTraceId,
        @JsonProperty("runtime_identity") AiReceiptRuntimeIdentity runtimeIdentity) {
    public AiReceiptAnalysisResponse(
            List<Item> receipts,
            Integer receiptCount,
            List<String> warnings,
            String ocrEngine,
            Boolean usedAi) {
        this(receipts, receiptCount, warnings, ocrEngine, usedAi, null, null);
    }

    public AiReceiptAnalysisResponse(
            List<Item> receipts, Integer receiptCount, List<String> warnings,
            String ocrEngine, Boolean usedAi, String analysisTraceId) {
        this(receipts, receiptCount, warnings, ocrEngine, usedAi, analysisTraceId, null);
    }

    // Strings permit validation of each item without discarding the remaining receipts.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("receipt_id") String receiptId,
            String title,
            @JsonProperty("store_name") String storeName,
            @JsonProperty("branch_name") String branchName,
            @JsonProperty("merchant_evidence") String merchantEvidence,
            @JsonProperty("branch_evidence") String branchEvidence,
            @JsonProperty("bounding_box") List<Double> boundingBox,
            @JsonProperty("identity_source_box") List<Double> identitySourceBox,
            @JsonProperty("identity_verification") String identityVerification,
            @JsonProperty("financial_source_box") List<Double> financialSourceBox,
            @JsonProperty("financial_recovery_provenance") String financialRecoveryProvenance,
            @JsonProperty("purchase_total") String purchaseTotal,
            @JsonProperty("payment_breakdown") List<PaymentItem> paymentBreakdown,
            @JsonProperty("cash_tendered") String cashTendered,
            String change,
            @JsonProperty("book_amount") String bookAmount,
            @JsonProperty("amount_policy_version") String amountPolicyVersion,
            @JsonProperty("amount_reason") String amountReason,
            @JsonProperty("review_status") String reviewStatus,
            @JsonProperty("original_amount") String originalAmount,
            @JsonProperty("detected_currency_code") String detectedCurrencyCode,
            @JsonProperty("transaction_date") String transactionDate,
            @JsonProperty("transaction_time") String transactionTime,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("category_source") String categorySource,
            @JsonProperty("category_reason") String categoryReason,
            String memo,
            Double confidence,
            @JsonProperty("detected_language") String detectedLanguage,
            String status,
            List<String> warnings) {
        public Item(
                String receiptId, String title, String storeName, String originalAmount,
                String detectedCurrencyCode, String transactionDate, String categoryName,
                String memo, Double confidence, String detectedLanguage, String status,
                List<String> warnings) {
            this(receiptId, title, storeName, null, null, null, null, null, null, null, null, originalAmount, List.of(),
                    null, null,
                    originalAmount, null, null, null, originalAmount, detectedCurrencyCode,
                    transactionDate, null, categoryName, null, null, memo, confidence, detectedLanguage, status,
                    warnings);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentItem(
            @JsonProperty("payment_type") String paymentType,
            String amount,
            String evidence,
            @JsonProperty("duplicate_group") String duplicateGroup) {
    }
}
