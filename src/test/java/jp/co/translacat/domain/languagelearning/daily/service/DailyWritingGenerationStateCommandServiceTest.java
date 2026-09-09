package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DailyWritingGenerationStateCommandServiceTest {

    private final DailyWritingSetRepository sets = mock(DailyWritingSetRepository.class);
    private final DailyWritingItemRepository items = mock(DailyWritingItemRepository.class);
    private final DailyWritingItemCommandService itemCommands = mock(DailyWritingItemCommandService.class);
    private final DailyWritingGenerationStateCommandService service =
            new DailyWritingGenerationStateCommandService(sets, itemCommands, items);
    private DailyWritingSet dailySet;

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        dailySet = DailyWritingSet.createGenerating(user, LocalDate.now(), DailyWritingType.FREE,
                "snapshot", 5, "{}");
        ReflectionTestUtils.setField(dailySet, "id", 11L);
        when(sets.findLockedById(11L)).thenReturn(Optional.of(dailySet));
        when(items.findAllByDailySetIdOrderByOrderNoAsc(11L)).thenReturn(List.of());
    }

    @Test
    void claimsFirstMissingSlotAndRejectsConcurrentClaim() {
        when(items.findAllByDailySetIdOrderByOrderNoAsc(11L)).thenReturn(List.of(item(1), item(3)));

        var claim = service.claim(11L);

        assertThat(claim.order()).isEqualTo(2);
        assertThat(claim.token()).isEqualTo(dailySet.getGenerationToken());
        assertThat(service.claim(11L)).isNull();
    }

    @Test
    void expiredWorkerCannotPublishOrEraseNewWorkerFailure() {
        dailySet.claimGeneration("expired", LocalDateTime.now().minusSeconds(1));
        var current = service.claim(11L);

        assertThat(service.publish(11L, "expired", 1, "ja", generated(), "prompt")).isFalse();
        service.fail(11L, "expired", "late failure");

        assertThat(dailySet.ownsGeneration(current.token())).isTrue();
        assertThat(dailySet.getFailureMessage()).isNull();
        verifyNoInteractions(itemCommands);
    }

    @Test
    void partialFailureRetainsItemsAndExplicitRetryResumesMissingSlot() {
        when(items.findAllByDailySetIdOrderByOrderNoAsc(11L)).thenReturn(List.of(item(1), item(2)));
        var claim = service.claim(11L);

        service.fail(11L, claim.token(), "third failed");

        assertThat(dailySet.getStatus()).isEqualTo(DailySetStatus.PARTIAL);
        assertThat(dailySet.getFailureMessage()).isEqualTo("third failed");
        assertThat(service.claim(11L)).isNull();
        service.retry(7L, 11L);
        var resumed = service.claim(11L);
        assertThat(resumed.order()).isEqualTo(3);
        assertThat(resumed.token()).isNotEqualTo(claim.token());
        verifyNoInteractions(itemCommands);
        verify(items, never()).deleteAll(any(Iterable.class));
    }

    @Test
    void firstSlotFailureIsFailedAndDoesNotAutoClaim() {
        var claim = service.claim(11L);
        service.fail(11L, claim.token(), "provider failed");
        assertThat(dailySet.getStatus()).isEqualTo(DailySetStatus.FAILED);
        assertThat(service.claim(11L)).isNull();
    }

    @Test
    void duplicateRetryDoesNotRevokeActiveLease() {
        var claim = service.claim(11L);
        service.retry(7L, 11L);
        assertThat(dailySet.ownsGeneration(claim.token())).isTrue();
    }

    @Test
    void rejectsRetryByAnotherUser() {
        assertThatThrownBy(() -> service.retry(9L, 11L)).isInstanceOf(BusinessException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesOneItemAtGlobalOrderAndKeepsSetGenerating() {
        when(items.findAllByDailySetIdOrderByOrderNoAsc(11L)).thenReturn(List.of(item(1), item(2)));
        var claim = service.claim(11L);
        when(items.findAllByDailySetIdOrderByOrderNoAsc(11L))
                .thenReturn(List.of(item(1), item(2)), List.of(item(1), item(2), item(3)));

        assertThat(service.publish(11L, claim.token(), 3, "ja", generated(), "prompt")).isTrue();

        ArgumentCaptor<List<DailyWritingGeneratedItemDto>> captured = ArgumentCaptor.forClass(List.class);
        verify(itemCommands).createAll(eq(dailySet), eq("ja"), captured.capture());
        assertThat(captured.getValue()).hasSize(1);
        assertThat(captured.getValue().getFirst().order()).isEqualTo(3);
        assertThat(dailySet.getStatus()).isEqualTo(DailySetStatus.GENERATING);
        assertThat(dailySet.getGenerationToken()).isNull();
        assertThat(service.publish(11L, claim.token(), 3, "ja", generated(), "prompt")).isFalse();
    }

    @Test
    void onlyFinalItemMakesSetReady() {
        var four = List.of(item(1), item(2), item(3), item(4));
        when(items.findAllByDailySetIdOrderByOrderNoAsc(11L)).thenReturn(four);
        var claim = service.claim(11L);
        when(items.findAllByDailySetIdOrderByOrderNoAsc(11L))
                .thenReturn(four, List.of(item(1), item(2), item(3), item(4), item(5)));

        service.publish(11L, claim.token(), 5, "ja", generated(), "prompt");

        assertThat(dailySet.getStatus()).isEqualTo(DailySetStatus.READY);
        assertThat(dailySet.getCompletedAt()).isNull();
    }

    private DailyWritingItem item(int order) {
        DailyWritingItem item = mock(DailyWritingItem.class);
        when(item.getOrderNo()).thenReturn(order);
        return item;
    }

    private DailyWritingGeneratedItemDto generated() {
        return new DailyWritingGeneratedItemDto(1, DailyWritingDifficulty.NORMAL, "작문 문제",
                List.of(), List.of(), "focus");
    }
}
