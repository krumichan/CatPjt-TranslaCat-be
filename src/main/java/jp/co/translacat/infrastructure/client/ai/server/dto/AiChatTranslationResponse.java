package jp.co.translacat.infrastructure.client.ai.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiChatTranslationResponse(
        @JsonProperty("translated_text")
        String translatedText
) {
}