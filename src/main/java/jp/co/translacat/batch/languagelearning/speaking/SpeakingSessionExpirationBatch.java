package jp.co.translacat.batch.languagelearning.speaking;

import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpeakingSessionExpirationBatch {
    private final SpeakingSessionExpirationService expirationService;

    @Scheduled(cron = "${language-learning.speaking.session-expire-cron:0 */10 * * * *}")
    public void expireInactiveSessions() {
        expirationService.expireInactiveSessions();
    }
}
