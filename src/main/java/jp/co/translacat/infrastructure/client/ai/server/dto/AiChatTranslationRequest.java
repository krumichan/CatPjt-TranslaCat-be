package jp.co.translacat.infrastructure.client.ai.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiChatTranslationRequest(
        String text,

        @JsonProperty("target_language_code")
        String targetLanguageCode
) {
}