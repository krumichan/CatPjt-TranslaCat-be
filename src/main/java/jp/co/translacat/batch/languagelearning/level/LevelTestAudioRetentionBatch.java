package jp.co.translacat.batch.languagelearning.level;

import jp.co.translacat.domain.languagelearning.level.service.LevelTestAudioRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LevelTestAudioRetentionBatch {
    private final LevelTestAudioRetentionService retentionService;

    @Scheduled(cron = "${language-learning.level-test.audio-cleanup-cron:0 35 4 * * *}")
    public void deleteExpiredAudio() {
        retentionService.deleteExpired();
    }
}
