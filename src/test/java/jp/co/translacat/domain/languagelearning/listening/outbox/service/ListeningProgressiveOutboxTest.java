package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.*;
import jp.co.translacat.domain.languagelearning.listening.daily.service.*;
import jp.co.translacat.domain.languagelearning.listening.evaluation.service.ListeningEvaluationWorker;
import jp.co.translacat.domain.languagelearning.listening.outbox.entity.ListeningOutboxEvent;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.profile.service.ListeningProfileRecalculationCommandService;
import jp.co.translacat.domain.languagelearning.listening.recommendation.service.ListeningRecommendationExplanationWorker;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListeningProgressiveOutboxTest {
    @Test
    void onlyCurrentDeliveryMayExtendALongRunningAiLease() {
        var repository = mock(ListeningOutboxEventRepository.class);
        var service = new ListeningOutboxTransactionService(repository);
        LocalDateTime now = LocalDateTime.now();
        var event = ListeningOutboxEvent.create(ListeningOutboxType.GENERATE_SET,
                10L, "{}", "key", now);
        event.claim();
        event.reclaim(now);
        event.claim();
        when(repository.findLockedById(1L)).thenReturn(Optional.of(event));
        var stale = new ListeningOutboxTransactionService.ClaimedEvent(1L,
                ListeningOutboxType.GENERATE_SET, 10L, "{}", "key", 1);
        var current = new ListeningOutboxTransactionService.ClaimedEvent(1L,
                ListeningOutboxType.GENERATE_SET, 10L, "{}", "key", 2);
        service.renewClaim(current, now);
        assertThat(event.getUpdatedAt()).isEqualTo(now);
        service.renewClaim(stale, now.plusMinutes(1));
        assertThat(event.getUpdatedAt()).isEqualTo(now);
        assertThat(event.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void oldClaimCannotFailReclaimedDelivery() {
        var repository = mock(ListeningOutboxEventRepository.class);
        var service = new ListeningOutboxTransactionService(repository);
        var event = ListeningOutboxEvent.create(ListeningOutboxType.GENERATE_SET,
                10L, "{}", "key", LocalDateTime.now());
        event.claim();
        var old = new ListeningOutboxTransactionService.ClaimedEvent(1L,
                ListeningOutboxType.GENERATE_SET, 10L, "{}", "key", 1);
        event.reclaim(LocalDateTime.now());
        event.claim();
        when(repository.findLockedById(1L)).thenReturn(Optional.of(event));
        assertThat(service.ownsClaim(old)).isFalse();
        assertThat(service.fail(old, "late", false, Duration.ZERO, 2, LocalDateTime.now()).exhausted())
                .isFalse();
        assertThat(event.getStatus()).isEqualTo(ListeningOutboxStatus.PROCESSING);
        assertThat(event.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void generatedAndAudioEventsUseSeparateWorkersWithoutSchedulerBlocking() {
        var transactions = mock(ListeningOutboxTransactionService.class);
        var generation = mock(ListeningGenerationWorker.class);
        var audio = mock(ListeningTtsWorker.class);
        List<Runnable> generationTasks = new ArrayList<>();
        List<Runnable> audioTasks = new ArrayList<>();
        var dispatcher = dispatcher(transactions, generation, audio,
                generationTasks::add, audioTasks::add);
        when(transactions.pendingEvents(any())).thenReturn(List.of(
                new ListeningOutboxTransactionService.PendingEvent(1L, ListeningOutboxType.GENERATE_SET),
                new ListeningOutboxTransactionService.PendingEvent(2L, ListeningOutboxType.GENERATE_TTS)));
        dispatcher.dispatch();
        assertThat(generationTasks).hasSize(1);
        assertThat(audioTasks).hasSize(1);
        verify(transactions, never()).claim(anyLong(), any());

        var event = new ListeningOutboxTransactionService.ClaimedEvent(2L,
                ListeningOutboxType.GENERATE_TTS, 20L, "{}", "audio", 1);
        when(transactions.claim(eq(2L), any())).thenReturn(Optional.of(event));
        audioTasks.get(0).run();
        verify(audio).process(event);
        verifyNoInteractions(generation);
    }

    @Test
    void saturatedGenerationLaneDoesNotClaimOrBlockAudioLane() {
        var transactions = mock(ListeningOutboxTransactionService.class);
        TaskExecutor full = task -> { throw new TaskRejectedException("busy"); };
        List<Runnable> audioTasks = new ArrayList<>();
        var dispatcher = dispatcher(transactions, mock(ListeningGenerationWorker.class),
                mock(ListeningTtsWorker.class), full, audioTasks::add);
        when(transactions.pendingEvents(any())).thenReturn(List.of(
                new ListeningOutboxTransactionService.PendingEvent(1L, ListeningOutboxType.GENERATE_SET),
                new ListeningOutboxTransactionService.PendingEvent(2L, ListeningOutboxType.GENERATE_TTS)));
        dispatcher.dispatch();
        assertThat(audioTasks).hasSize(1);
        verify(transactions, never()).claim(anyLong(), any());
    }

    private ListeningOutboxDispatcher dispatcher(
            ListeningOutboxTransactionService transactions,
            ListeningGenerationWorker generation,
            ListeningTtsWorker audio,
            TaskExecutor generationExecutor,
            TaskExecutor audioExecutor
    ) {
        return new ListeningOutboxDispatcher(transactions, generation, audio,
                mock(ListeningEvaluationWorker.class), mock(ListeningProfileRecalculationCommandService.class),
                mock(ListeningRecommendationExplanationWorker.class),
                generationExecutor, audioExecutor, Runnable::run);
    }
}
