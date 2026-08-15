package jp.co.translacat.domain.languagelearning.speaking.audio.service;

import jp.co.translacat.domain.languagelearning.speaking.audio.port.SpeakingAudioStoragePort;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingAudioRetentionService {

    private final SpeakingTurnRepository turnRepository;
    private final SpeakingSessionRepository sessionRepository;
    private final SpeakingAudioStoragePort audioStoragePort;

    @Scheduled(cron = "${language-learning.speaking.audio-cleanup-cron:0 20 4 * * *}")
    @Transactional
    public void deleteExpiredAudio() {
        LocalDateTime now = LocalDateTime.now();
        for (SpeakingTurn turn : turnRepository
                .findAllByUserAudioRetentionUntilBeforeAndUserAudioObjectKeyIsNotNull(
                        now
                )) {
            if (deleteSafely(turn.getUserAudioObjectKey())) {
                turn.clearUserAudio();
            }
        }

        for (SpeakingTurn turn : turnRepository
                .findAllByAssistantAudioRetentionUntilBeforeAndAssistantAudioObjectKeyIsNotNull(
                        now
                )) {
            if (deleteSafely(turn.getAssistantAudioObjectKey())) {
                turn.clearAssistantAudio();
            }
        }

        for (SpeakingSession session : sessionRepository
                .findAllByOpeningAssistantAudioRetentionUntilBeforeAndOpeningAssistantAudioObjectKeyIsNotNull(
                        now
                )) {
            if (deleteSafely(session.getOpeningAssistantAudioObjectKey())) {
                session.clearOpeningAssistantAudio();
            }
        }
    }

    private boolean deleteSafely(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return true;
        }
        try {
            audioStoragePort.delete(objectKey);
            return true;
        } catch (RuntimeException e) {
            log.warn(
                    "Speaking audio retention delete failed. objectKey={}",
                    objectKey,
                    e
            );
            return false;
        }
    }
}
