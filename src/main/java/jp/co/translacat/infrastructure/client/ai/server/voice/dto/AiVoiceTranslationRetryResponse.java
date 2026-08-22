package jp.co.translacat.infrastructure.client.ai.server.voice.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AiVoiceTranslationRetryResponse(
        String translatedText,
        JsonNode sourceReadingTokens,
        boolean translationSkipped,
        AiVoiceModelResponse model
) {
}
