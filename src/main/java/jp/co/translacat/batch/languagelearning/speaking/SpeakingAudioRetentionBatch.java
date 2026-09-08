package jp.co.translacat.batch.languagelearning.speaking;

import jp.co.translacat.domain.languagelearning.speaking.audio.service.SpeakingAudioRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpeakingAudioRetentionBatch {
    private final SpeakingAudioRetentionService retentionService;

    @Scheduled(cron = "${language-learning.speaking.audio-cleanup-cron:0 20 4 * * *}")
    public void deleteExpiredAudio() {
        retentionService.deleteExpiredAudio();
    }
}
