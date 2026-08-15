package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingMetricEvidenceDto(
        String turnId,
        Integer startMs,
        Integer endMs,
        String message
) {
}
