package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DailyWritingGenerationRecoveryServiceTest {

    private final DailyWritingSetRepository sets = mock(DailyWritingSetRepository.class);
    private final DailyWritingGenerationExecutor executor = mock(DailyWritingGenerationExecutor.class);
    private final DailyWritingGenerationRecoveryService service = new DailyWritingGenerationRecoveryService(sets, executor);

    @Test
    void scannerRecoversUnclaimedAndExpiredJobsAfterRestart() {
        DailyWritingSet unclaimed = mock(DailyWritingSet.class);
        DailyWritingSet expired = mock(DailyWritingSet.class);
        when(unclaimed.getId()).thenReturn(11L);
        when(expired.getId()).thenReturn(12L);
        when(executor.execute(11L)).thenReturn(new CompletableFuture<>());
        when(executor.execute(12L)).thenReturn(new CompletableFuture<>());
        when(sets.findTop20ByStatusAndGenerationLeaseUntilIsNullOrderByIdAsc(DailySetStatus.GENERATING))
                .thenReturn(List.of(unclaimed));
        when(sets.findTop20ByStatusAndGenerationLeaseUntilLessThanEqualOrderByIdAsc(
                eq(DailySetStatus.GENERATING), any(LocalDateTime.class))).thenReturn(List.of(expired));

        service.recover();

        verify(executor).execute(11L);
        verify(executor).execute(12L);
    }

    @Test
    void saturatedQueueDoesNotFailTheRequestOrMarkPersistedWorkFailed() {
        when(executor.execute(11L)).thenThrow(new TaskRejectedException("queue full"))
                .thenReturn(new CompletableFuture<>());

        assertThatCode(() -> service.dispatch(11L)).doesNotThrowAnyException();
        service.dispatch(11L);

        verify(executor, times(2)).execute(11L);
        verifyNoInteractions(sets);
    }

    @Test
    void pendingWorkerIsEnqueuedOnlyOnceAndCompletionReleasesId() {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        when(executor.execute(11L)).thenReturn(pending).thenReturn(new CompletableFuture<>());
        service.dispatch(11L);
        service.dispatch(11L);
        service.dispatch(11L);
        verify(executor).execute(11L);

        pending.complete(null);
        service.dispatch(11L);
        verify(executor, times(2)).execute(11L);
    }

    @Test
    void exceptionalCompletionAlsoReleasesIdForRecovery() {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        when(executor.execute(11L)).thenReturn(pending).thenReturn(new CompletableFuture<>());
        service.dispatch(11L);
        pending.completeExceptionally(new IllegalStateException("worker interrupted"));
        service.dispatch(11L);
        verify(executor, times(2)).execute(11L);
    }
}
