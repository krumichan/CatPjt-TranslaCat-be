package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.entity.ListeningOutboxEvent;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListeningTtsRecoveryServiceTest {

    @Mock
    private ListeningItemRepository itemRepository;
    @Mock
    private ListeningOutboxEventRepository outboxRepository;
    @Mock
    private ListeningOutboxCommandService outboxCommandService;
    @Mock
    private ListeningTtsTransactionService ttsTransactionService;
    @Mock
    private ListeningItem item;
    @Mock
    private ListeningDailySet dailySet;

    private ListeningTtsRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new ListeningTtsRecoveryService(
                itemRepository,
                outboxRepository,
                outboxCommandService,
                ttsTransactionService
        );
    }

    @Test
    void recreatesMissingTtsOutboxForPendingItem() {
        when(itemRepository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(ListeningItemStatus.TTS_PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(item));
        when(item.getId()).thenReturn(30007L);
        when(item.getStatus()).thenReturn(ListeningItemStatus.TTS_PENDING);
        when(item.getDailySet()).thenReturn(dailySet);
        when(dailySet.getId()).thenReturn(90001L);
        when(itemRepository.findLockedById(30007L)).thenReturn(Optional.of(item));
        when(outboxRepository.findFirstByEventTypeAndAggregateIdOrderByIdDesc(
                ListeningOutboxType.GENERATE_TTS,
                30007L
        )).thenReturn(Optional.empty());

        service.recoverOrphans();

        verify(outboxCommandService).enqueue(
                ListeningOutboxType.GENERATE_TTS,
                30007L,
                null,
                "listening:item:30007:tts:recovery:missing"
        );
    }

    @Test
    void doesNotDuplicateActiveTtsOutbox() {
        ListeningOutboxEvent pending = ListeningOutboxEvent.create(
                ListeningOutboxType.GENERATE_TTS,
                30007L,
                "{}",
                "listening:item:30007:tts:0",
                LocalDateTime.now()
        );
        when(itemRepository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(ListeningItemStatus.TTS_PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(item));
        when(item.getId()).thenReturn(30007L);
        when(item.getStatus()).thenReturn(ListeningItemStatus.TTS_PENDING);
        when(itemRepository.findLockedById(30007L)).thenReturn(Optional.of(item));
        when(outboxRepository.findFirstByEventTypeAndAggregateIdOrderByIdDesc(
                ListeningOutboxType.GENERATE_TTS,
                30007L
        )).thenReturn(Optional.of(pending));

        service.recoverOrphans();

        verify(outboxCommandService, never()).enqueue(
                any(ListeningOutboxType.class),
                anyLong(),
                any(),
                anyString()
        );
        verify(ttsTransactionService, never()).abandonOrphan(
                anyLong(),
                anyString()
        );
    }
}
