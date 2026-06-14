package jp.co.translacat.infrastructure.client.ai.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiReceiptAnalysisOptions(
        @JsonProperty("currency_code")
        String currencyCode,

        @JsonProperty("ocr_language")
        String ocrLanguage,

        @JsonProperty("stop_keywords")
        List<String> stopKeywords,

        @JsonProperty("important_keywords")
        List<String> importantKeywords,

        @JsonProperty("exclude_item_keywords")
        List<String> excludeItemKeywords
) {
}