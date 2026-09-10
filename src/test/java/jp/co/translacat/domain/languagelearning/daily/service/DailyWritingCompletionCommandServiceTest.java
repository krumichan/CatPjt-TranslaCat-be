package jp.co.translacat.domain.languagelearning.daily.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.daily.entity.*;
import jp.co.translacat.domain.languagelearning.daily.repository.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DailyWritingCompletionCommandServiceTest {

    private final DailyWritingSetRepository sets = mock(DailyWritingSetRepository.class);
    private final DailyWritingItemRepository items = mock(DailyWritingItemRepository.class);
    private final WritingAnswerRepository answers = mock(WritingAnswerRepository.class);
    private final WritingEvaluationRepository evaluations = mock(WritingEvaluationRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final DailyWritingCompletionCommandService service =
            new DailyWritingCompletionCommandService(sets, items, answers, evaluations, entityManager);

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void fewerThanTargetItemsNeverCompletes(int count) {
        DailyWritingSet set = set();
        set.ready("prompt");
        List<DailyWritingItem> generated = IntStream.rangeClosed(1, count)
                .mapToObj(this::item)
                .toList();
        when(items.findAllByDailySetIdOrderByOrderNoAsc(11L)).thenReturn(generated);

        service.completeIfAllEvaluated(11L);

        assertThat(set.getStatus()).isEqualTo(DailySetStatus.READY);
        verifyNoInteractions(answers, evaluations);
    }

    @Test
    void generationInProgressNeverCompletes() {
        DailyWritingSet set = set();
        service.completeIfAllEvaluated(11L);
        assertThat(set.getStatus()).isEqualTo(DailySetStatus.GENERATING);
        verify(entityManager).refresh(set, LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(items, answers, evaluations);
    }

    @Test
    void allFiveEvaluationsCompletesReadySet() {
        DailyWritingSet set = set();
        set.ready("prompt");
        List<DailyWritingItem> generated = IntStream.rangeClosed(1, 5).mapToObj(this::item).toList();
        when(items.findAllByDailySetIdOrderByOrderNoAsc(11L)).thenReturn(generated);
        for (int order = 1; order <= 5; order++) {
            WritingAnswer answer = mock(WritingAnswer.class);
            WritingEvaluation evaluation = mock(WritingEvaluation.class);
            when(answer.getId()).thenReturn((long) order);
            when(answers.findAllByDailyItemIdOrderByAttemptDateAsc((long) order)).thenReturn(List.of(answer));
            when(evaluations.findByAnswerId((long) order)).thenReturn(Optional.of(evaluation));
            when(evaluation.getStatus()).thenReturn(EvaluationStatus.SUCCESS);
        }

        service.completeIfAllEvaluated(11L);

        assertThat(set.getStatus()).isEqualTo(DailySetStatus.COMPLETED);
    }

    @Test
    void returnsRefreshedLockedEntityForPollingResponse() {
        DailyWritingSet stale = set();
        stale.ready("prompt");
        doAnswer(ignored -> {
            stale.complete();
            return null;
        }).when(entityManager).refresh(stale, LockModeType.PESSIMISTIC_WRITE);

        DailyWritingSet refreshed = service.completeIfAllEvaluated(11L);

        assertThat(refreshed).isSameAs(stale);
        assertThat(refreshed.getStatus()).isEqualTo(DailySetStatus.COMPLETED);
        verifyNoInteractions(items, answers, evaluations);
    }

    private DailyWritingSet set() {
        DailyWritingSet set = DailyWritingSet.createGenerating(null, LocalDate.now(), DailyWritingType.FREE,
                "snapshot", 5, "{}");
        ReflectionTestUtils.setField(set, "id", 11L);
        when(sets.findLockedById(11L)).thenReturn(Optional.of(set));
        return set;
    }

    private DailyWritingItem item(int order) {
        DailyWritingItem item = mock(DailyWritingItem.class);
        when(item.getId()).thenReturn((long) order);
        when(item.getOrderNo()).thenReturn(order);
        return item;
    }
}
