package jp.co.translacat.batch.languagelearning.listening;

import jp.co.translacat.domain.languagelearning.listening.audio.service.ListeningAudioRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListeningAudioRetentionBatch {
    private final ListeningAudioRetentionService retentionService;

    @Scheduled(cron = "${language-learning.listening.audio-cleanup-cron:0 35 4 * * *}")
    public void deleteExpiredAudio() {
        retentionService.deleteExpired();
    }
}
