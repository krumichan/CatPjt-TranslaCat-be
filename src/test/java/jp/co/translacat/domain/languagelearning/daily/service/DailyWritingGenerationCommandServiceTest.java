package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingGenerationContext;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DailyWritingGenerationCommandServiceTest {

    private final DailyWritingSetRepository sets = mock(DailyWritingSetRepository.class);
    private final DailySetClaimCommandService claims = mock(DailySetClaimCommandService.class);
    private final DailyWritingGenerationContextService contexts = mock(DailyWritingGenerationContextService.class);
    private final DailyWritingSnapshotService snapshots = mock(DailyWritingSnapshotService.class);
    private final DailyWritingGenerationStateCommandService state = mock(DailyWritingGenerationStateCommandService.class);
    private final DailyWritingGenerationRecoveryService recovery = mock(DailyWritingGenerationRecoveryService.class);
    private final DailyWritingCompletionCommandService completion = mock(DailyWritingCompletionCommandService.class);
    private final DailyWritingGenerationCommandService service =
            new DailyWritingGenerationCommandService(sets, claims, contexts, snapshots, state, recovery, completion);

    @Test
    void pollingGeneratingSetReturnsExistingItemsWithoutThrowingOrDispatching() {
        DailyWritingSet set = existing();
        assertThat(service.getOrGenerateToday(7L, DailyWritingType.FREE)).isSameAs(set);
        verifyNoInteractions(claims, snapshots, state, recovery, completion);
    }

    @Test
    void pollingFailedOrPartialSetDoesNotImplicitlyRetry() {
        DailyWritingSet set = existing();
        set.failGeneration("provider failure", true);
        assertThat(service.getOrGenerateToday(7L, DailyWritingType.FREE)).isSameAs(set);
        set.fail("provider failure");
        assertThat(service.getOrGenerateToday(7L, DailyWritingType.FREE)).isSameAs(set);
        verifyNoInteractions(claims, snapshots, state, recovery, completion);
    }

    @Test
    void readyPollingReconcilesCommittedEvaluationsAndReturnsFreshEntity() {
        DailyWritingSet stale = existing();
        ReflectionTestUtils.setField(stale, "id", 11L);
        stale.ready("prompt");
        DailyWritingSet refreshed = DailyWritingSet.createGenerating(null, LocalDate.now(), DailyWritingType.FREE,
                "snapshot", 5, "{}");
        refreshed.complete();
        when(completion.completeIfAllEvaluated(11L)).thenReturn(refreshed);

        assertThat(service.getOrGenerateToday(7L, DailyWritingType.FREE)).isSameAs(refreshed);

        verifyNoInteractions(claims, snapshots, state, recovery);
    }

    @Test
    void explicitRetryDispatchesOnlyAfterStateCommandReturns() {
        DailyWritingSet set = DailyWritingSet.createGenerating(null, LocalDate.now(), DailyWritingType.FREE,
                "snapshot", 5, "{}");
        when(state.retry(7L, 11L)).thenReturn(set);

        assertThat(service.retryGeneration(7L, 11L)).isSameAs(set);

        var ordered = inOrder(state, recovery);
        ordered.verify(state).retry(7L, 11L);
        ordered.verify(recovery).dispatch(11L);
    }

    private DailyWritingSet existing() {
        LocalDate today = LocalDate.now();
        DailyWritingSet set = DailyWritingSet.createGenerating(null, today, DailyWritingType.FREE,
                "snapshot", 5, "{}");
        when(contexts.prepare(7L)).thenReturn(new DailyWritingGenerationContext(null, null, today, 5, null));
        when(sets.findByUserIdAndLearningDateAndWritingType(7L, today, DailyWritingType.FREE))
                .thenReturn(Optional.of(set));
        return set;
    }
}
