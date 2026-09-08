package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.entity.ListeningOutboxEvent;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListeningTtsRecoveryService {

    private static final String RECOVERY_KEY_MARKER = ":tts:recovery:";

    private final ListeningItemRepository itemRepository;
    private final ListeningOutboxEventRepository outboxRepository;
    private final ListeningOutboxCommandService outboxCommandService;
    private final ListeningTtsTransactionService ttsTransactionService;

    @Value("${language-learning.listening.tts-recovery-grace-seconds:15}")
    private long recoveryGraceSeconds;

    @Transactional
    public void recoverOrphans() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusSeconds(Math.max(5L, recoveryGraceSeconds));

        for (ListeningItem candidate : itemRepository
                .findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        ListeningItemStatus.TTS_PENDING,
                        cutoff
                )) {
            recover(candidate.getId());
        }
    }

    private void recover(Long itemId) {
        ListeningItem item = itemRepository.findLockedById(itemId)
                .orElse(null);
        if (item == null || item.getStatus() != ListeningItemStatus.TTS_PENDING) {
            return;
        }

        Optional<ListeningOutboxEvent> latestOptional = outboxRepository
                .findFirstByEventTypeAndAggregateIdOrderByIdDesc(
                        ListeningOutboxType.GENERATE_TTS,
                        itemId
                );

        if (latestOptional.isEmpty()) {
            enqueueRecovery(itemId, "missing");
            log.warn(
                    "Listening TTS orphan recovered: outbox missing. itemId={} dailySetId={}",
                    itemId,
                    item.getDailySet().getId()
            );
            return;
        }

        ListeningOutboxEvent latest = latestOptional.get();
        if (latest.getStatus() == ListeningOutboxStatus.PENDING
                || latest.getStatus() == ListeningOutboxStatus.PROCESSING) {
            return;
        }

        if (latest.getIdempotencyKey().contains(RECOVERY_KEY_MARKER)) {
            log.error(
                    "Listening TTS orphan recovery exhausted. itemId={} dailySetId={} "
                            + "latestEventId={} latestStatus={}",
                    itemId,
                    item.getDailySet().getId(),
                    latest.getId(),
                    latest.getStatus()
            );
            ttsTransactionService.abandonOrphan(
                    itemId,
                    "TTS 복구 작업이 완료되지 않아 대체 문항 생성을 시도합니다."
            );
            return;
        }

        enqueueRecovery(itemId, "after-" + latest.getId());
        log.warn(
                "Listening TTS orphan recovered from terminal outbox. itemId={} "
                        + "dailySetId={} latestEventId={} latestStatus={}",
                itemId,
                item.getDailySet().getId(),
                latest.getId(),
                latest.getStatus()
        );
    }

    private void enqueueRecovery(Long itemId, String suffix) {
        outboxCommandService.enqueue(
                ListeningOutboxType.GENERATE_TTS,
                itemId,
                null,
                "listening:item:" + itemId + RECOVERY_KEY_MARKER + suffix
        );
    }
}
