package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ListeningTtsRecoveryService {

    private final ListeningItemRepository itemRepository;
    private final ListeningTtsTransactionService ttsTransactionService;

    @Value("${language-learning.listening.tts-recovery-grace-seconds:15}")
    private long recoveryGraceSeconds;

    public void recoverOrphans() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusSeconds(Math.max(5L, recoveryGraceSeconds));

        for (ListeningItem candidate : itemRepository
                .findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        ListeningItemStatus.TTS_PENDING,
                        cutoff
                )) {
            // Each recovery owns one aggregate transaction, never locks across sets.
            ttsTransactionService.recoverOrphan(candidate.getId());
        }
    }
}
