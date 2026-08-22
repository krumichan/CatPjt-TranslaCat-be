package jp.co.translacat.infrastructure.client.ai.server.voice.dto;

public record AiVoiceTranslationRetryRequest(
        String requestId,
        String sessionId,
        Long segmentId,
        String sourceText,
        String sourceLanguage,
        String targetLanguage
) {
}
