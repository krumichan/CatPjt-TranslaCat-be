package jp.co.translacat.infrastructure.client.ai.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiReceiptAnalysisOptions(
        @JsonProperty("ocr_language") String ocrLanguage,
        @JsonProperty("analysis_mode") String analysisMode,
        @JsonProperty("stop_keywords") List<String> stopKeywords,
        @JsonProperty("important_keywords") List<String> importantKeywords,
        @JsonProperty("exclude_item_keywords") List<String> excludeItemKeywords,
        @JsonProperty("category_candidates") List<String> categoryCandidates) {
    public AiReceiptAnalysisOptions withAnalysisMode(String mode) {
        return new AiReceiptAnalysisOptions(
                ocrLanguage,
                mode,
                stopKeywords,
                importantKeywords,
                excludeItemKeywords,
                categoryCandidates);
    }
}
