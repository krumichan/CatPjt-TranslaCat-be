package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingSessionStatus;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpeakingSessionExpirationService {

    private final SpeakingSessionRepository sessionRepository;
    private final SpeakingSessionLifecycleService lifecycleService;

    @Scheduled(
            cron = "${language-learning.speaking.session-expire-cron:0 */10 * * * *}"
    )
    @Transactional
    public void expireInactiveSessions() {
        for (SpeakingSession session : sessionRepository.findAllByStatus(
                SpeakingSessionStatus.IN_PROGRESS
        )) {
            lifecycleService.expireIfNeeded(session);
        }
    }
}
