package jp.co.translacat.infrastructure.client.ai.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AiReceiptAnalysisResponse(
        String title,

        @JsonProperty("store_name")
        String storeName,

        BigDecimal amount,

        @JsonProperty("transaction_date")
        LocalDate transactionDate,

        @JsonProperty("category_name")
        String categoryName,

        String memo,

        Double confidence,

        @JsonProperty("raw_text")
        String rawText,

        @JsonProperty("ocr_engine")
        String ocrEngine,

        @JsonProperty("used_ai")
        Boolean usedAi
) {
}