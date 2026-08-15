package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiSpeakingTranscriptDto(
        String text,
        String language,
        double confidence,
        @JsonProperty("isLowConfidence") boolean lowConfidence,
        List<AiSpeakingSttSegmentDto> segments,
        AiSpeakingSttAnalysisMetadataDto metadata
) {
}
