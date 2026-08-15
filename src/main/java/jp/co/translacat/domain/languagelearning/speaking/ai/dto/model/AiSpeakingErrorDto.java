package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingErrorDto(
        String code,
        String stage,
        String message,
        boolean retryable
) {
}
