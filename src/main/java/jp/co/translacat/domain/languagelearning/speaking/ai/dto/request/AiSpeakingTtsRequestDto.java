package jp.co.translacat.domain.languagelearning.speaking.ai.dto.request;

public record AiSpeakingTtsRequestDto(
        String requestId,
        String idempotencyKey,
        String sessionId,
        String text,
        String learningLanguage,
        String voice,
        String playbackSpeed,
        int automaticRetryLimit,
        int manualRetryAttempt
) {
}
