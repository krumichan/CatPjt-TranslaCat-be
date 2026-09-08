package jp.co.translacat.batch.voice;

import jp.co.translacat.domain.voice.service.VoiceHistoryCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VoiceHistoryCleanupBatch {
    private final VoiceHistoryCleanupService cleanupService;

    @Scheduled(
            fixedDelayString = "${translacat.voice.cleanup-fixed-delay-ms:60000}",
            initialDelayString = "${translacat.voice.cleanup-initial-delay-ms:60000}"
    )
    public void cleanupStaleSessions() {
        cleanupService.cleanupStaleSessions();
    }
}
