package jp.co.translacat.batch.chat;

public record ChatMessageTranslationRetryBatchResult(
        int targetCount,
        int successCount,
        int failedCount
) {
}
