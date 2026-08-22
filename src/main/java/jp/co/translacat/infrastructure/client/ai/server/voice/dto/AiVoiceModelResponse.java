package jp.co.translacat.infrastructure.client.ai.server.voice.dto;

public record AiVoiceModelResponse(
        String sttVersion,
        String translationVersion,
        String promptVersion
) {
}
