package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.languagelearning.speaking.audio.port.SpeakingAudioStoragePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LevelTestAudioRetentionService {

    private final LevelTestResponseRepository responseRepository;
    private final SpeakingAudioStoragePort storagePort;

    @Scheduled(
            cron = "${language-learning.level-test.audio-cleanup-cron:0 35 4 * * *}"
    )
    @Transactional
    public void deleteExpired() {
        LocalDateTime now = LocalDateTime.now();
        for (LevelTestResponse response : responseRepository
                .findAllByAudioObjectKeyIsNotNullAndAudioDeletedAtIsNullAndAudioRetentionUntilBefore(
                        now
                )) {
            try {
                storagePort.delete(response.getAudioObjectKey());
                response.markAudioDeleted(now);
            } catch (RuntimeException exception) {
                log.warn(
                        "Level Test audio retention delete failed. responseId={}",
                        response.getId(),
                        exception
                );
            }
        }
    }
}
