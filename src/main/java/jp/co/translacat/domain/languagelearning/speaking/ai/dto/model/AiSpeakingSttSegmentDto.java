package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingSttSegmentDto(
        int startMs,
        int endMs,
        String text,
        double confidence
) {
}
