package jp.co.translacat.batch.chat;

import jp.co.translacat.domain.chat.translation.service.ChatMessageTranslationProcessor;
import jp.co.translacat.domain.chat.translation.service.ChatMessageTranslationRetryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageTranslationRetryBatch {

    private final ChatMessageTranslationProcessor chatMessageTranslationProcessor;

    @Value("${translacat.batch.chat-translation-retry.enabled:true}")
    private boolean enabled;

    @Value("${translacat.batch.chat-translation-retry.limit:50}")
    private int limit;

    @Scheduled(
            fixedDelayString = "${translacat.batch.chat-translation-retry.fixed-delay-ms:60000}",
            initialDelayString = "${translacat.batch.chat-translation-retry.initial-delay-ms:30000}"
    )
    public void retryFailedChatMessageTranslations() {
        if (!enabled) {
            log.info("Chat message translation retry batch is disabled.");
            return;
        }

        log.info("Chat message translation retry batch tick. limit={}", limit);

        ChatMessageTranslationRetryResult result =
                chatMessageTranslationProcessor.retryFailedTranslations(limit);

        log.info(
                "Chat message translation retry batch finished. targetCount={}, successCount={}, failedCount={}",
                result.targetCount(),
                result.successCount(),
                result.failedCount()
        );
    }
}
