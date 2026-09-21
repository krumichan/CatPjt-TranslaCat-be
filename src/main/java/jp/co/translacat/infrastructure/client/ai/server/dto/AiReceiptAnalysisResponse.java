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
        @JsonProperty("used_ai") Boolean usedAi) {
    // Strings permit validation of each item without discarding the remaining receipts.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("receipt_id") String receiptId,
            String title,
            @JsonProperty("store_name") String storeName,
            @JsonProperty("original_amount") String originalAmount,
            @JsonProperty("detected_currency_code") String detectedCurrencyCode,
            @JsonProperty("transaction_date") String transactionDate,
            @JsonProperty("category_name") String categoryName,
            String memo,
            Double confidence,
            @JsonProperty("detected_language") String detectedLanguage,
            String status,
            List<String> warnings) {}
}
