package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ListeningTtsRecoveryServiceTest {
    @Test
    void delegatesEachOrphanToAnIndependentAggregateTransaction() {
        var items = mock(ListeningItemRepository.class);
        var transactions = mock(ListeningTtsTransactionService.class);
        var first = mock(ListeningItem.class);
        var second = mock(ListeningItem.class);
        when(first.getId()).thenReturn(1L);
        when(second.getId()).thenReturn(2L);
        when(items.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(ListeningItemStatus.TTS_PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));

        new ListeningTtsRecoveryService(items, transactions).recoverOrphans();

        verify(transactions).recoverOrphan(1L);
        verify(transactions).recoverOrphan(2L);
        verify(items, never()).findLockedById(any());
    }
}
