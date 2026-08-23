package jp.co.translacat.domain.languagelearning.listening.support;

public record ListeningErrorDto(
        String code,
        String messageKey,
        boolean retryable,
        Long retryAfterSeconds,
        String failedStage,
        Long resourceId
) {
}
