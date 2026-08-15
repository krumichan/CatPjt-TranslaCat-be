package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingAssistantAudioDto(
        String audioReference,
        String contentType,
        String voice,
        String cacheKey,
        Double durationSeconds,
        String status
) {
}
