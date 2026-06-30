package jp.co.translacat.domain.chat.translation.service;

public record ChatMessageTranslationRetryResult(
        int targetCount,
        int successCount,
        int failedCount
) {
}
