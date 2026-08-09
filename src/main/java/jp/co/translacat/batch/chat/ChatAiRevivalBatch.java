package jp.co.translacat.batch.chat;

import jp.co.translacat.domain.chat.ai.service.ChatAiRevivalProcessor;
import jp.co.translacat.domain.chat.ai.service.ChatAiRevivalScheduleCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAiRevivalBatch {

    private final ChatAiRevivalProcessor revivalProcessor;
    private final ChatAiRevivalScheduleCalculator scheduleCalculator;

    @Value("${translacat.batch.ai-revival.enabled:true}")
    private boolean enabled;

    @Value("${translacat.batch.ai-revival.limit:50}")
    private int limit;

    @Scheduled(
            fixedDelayString = "${translacat.batch.ai-revival.fixed-delay-ms:60000}",
            initialDelayString = "${translacat.batch.ai-revival.initial-delay-ms:45000}"
    )
    public void processDueRevivalRooms() {
        if (!enabled) {
            log.debug("AI REVIVAL batch is disabled.");
            return;
        }

        LocalDateTime now = scheduleCalculator.now();
        ChatAiRevivalProcessor.ChatAiRevivalBatchResult result =
                revivalProcessor.processDue(now, Math.max(1, limit));

        if (result.dueCount() == 0) {
            return;
        }
        log.info(
                "AI REVIVAL batch finished. dueCount={}, claimedCount={}, respondedCount={}, skippedCount={}, failedCount={}",
                result.dueCount(),
                result.claimedCount(),
                result.respondedCount(),
                result.skippedCount(),
                result.failedCount()
        );
    }
}
