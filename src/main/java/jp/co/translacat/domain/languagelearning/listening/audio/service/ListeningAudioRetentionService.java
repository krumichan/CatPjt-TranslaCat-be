package jp.co.translacat.domain.languagelearning.listening.audio.service;

import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ListeningAudioRetentionService {

    private final ListeningAudioRetentionTransactionService transactionService;
    private final ListeningAudioStoragePort storagePort;

    @Scheduled(
            cron = "${language-learning.listening.audio-cleanup-cron:0 35 4 * * *}"
    )
    public void deleteExpired() {
        LocalDateTime now = LocalDateTime.now();

        for (var audio : transactionService.expired(now)) {
            storagePort.delete(audio.objectKey());
            transactionService.markDeleted(audio, now);
        }
    }
}
