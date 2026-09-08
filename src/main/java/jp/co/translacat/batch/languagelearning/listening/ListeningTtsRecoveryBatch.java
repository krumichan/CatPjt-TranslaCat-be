package jp.co.translacat.batch.languagelearning.listening;

import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningTtsRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListeningTtsRecoveryBatch {
    private final ListeningTtsRecoveryService recoveryService;

    @Scheduled(fixedDelayString = "${language-learning.listening.tts-recovery-delay-ms:5000}")
    public void recoverOrphans() {
        recoveryService.recoverOrphans();
    }
}
