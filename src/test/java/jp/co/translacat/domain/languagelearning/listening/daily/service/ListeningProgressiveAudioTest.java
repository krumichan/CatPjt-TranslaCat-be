package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jakarta.persistence.EntityManager;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.audio.service.ListeningAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.listening.common.enums.*;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.*;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningGenerationCommand;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.*;
import jp.co.translacat.domain.languagelearning.listening.outbox.entity.ListeningOutboxEvent;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.*;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListeningProgressiveAudioTest {
    private final ListeningItemRepository items = mock(ListeningItemRepository.class);
    private final ListeningDailySetRepository sets = mock(ListeningDailySetRepository.class);
    private final ListeningPolicySettingQueryService policies = mock(ListeningPolicySettingQueryService.class);
    private final ListeningPolicySetting policy = mock(ListeningPolicySetting.class);
    private final ListeningOutboxCommandService enqueue = mock(ListeningOutboxCommandService.class);
    private final ListeningOutboxTransactionService outbox = mock(ListeningOutboxTransactionService.class);
    private final ListeningOutboxEventRepository events = mock(ListeningOutboxEventRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private ListeningDailySet set;
    private ListeningItem item;
    private ListeningTtsTransactionService service;
    private ListeningOutboxTransactionService.ClaimedEvent event;

    @BeforeEach
    void setup() {
        set = ListeningDailySet.create(null, LocalDate.now(), "ko", "ja",
                ListeningLearningMode.DICTATION, ListeningDifficulty.MY_LEVEL,
                "{}", "[]", "{}", "policy", 5);
        ReflectionTestUtils.setField(set, "id", 20L);
        set.recordPhysicalItem();
        item = ListeningItem.create(set, 1, "text", "text", "[]", "[]", "[]",
                5.0, "hash", "similarity", "{}", null, 0);
        ReflectionTestUtils.setField(item, "id", 100L);
        when(items.findById(100L)).thenReturn(Optional.of(item));
        when(items.findLockedById(100L)).thenReturn(Optional.of(item));
        when(sets.findLockedById(20L)).thenReturn(Optional.of(set));
        when(policies.get()).thenReturn(policy);
        when(policy.getHardItemLimit()).thenReturn(10);
        when(items.existsByDailySetIdAndItemIndex(20L, 1)).thenReturn(true);
        service = new ListeningTtsTransactionService(items, sets, policies,
                new ListeningAudioKeyFactory(), enqueue, outbox,
                mock(LanguageLearningJsonCodec.class), entityManager, events);
        event = new ListeningOutboxTransactionService.ClaimedEvent(30L,
                ListeningOutboxType.GENERATE_TTS, 100L, "{}", "key", 1);
        when(outbox.ownsClaim(event)).thenReturn(true);
    }

    @Test
    void lateAudioSuccessKeepsDownstreamGenerationFailure() {
        set.fail("slot 3 failed");
        when(items.countLogicalItemsByStatus(20L, ListeningItemStatus.READY)).thenReturn(1L);
        service.apply(work(), response(), "audio/wav");
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.READY);
        assertThat(set.getStatus()).isEqualTo(ListeningDailySetStatus.PARTIAL);
        assertThat(set.getFailureReason()).isEqualTo("slot 3 failed");
        var locks = inOrder(sets, items, entityManager);
        locks.verify(sets).findLockedById(20L);
        locks.verify(items).findLockedById(100L);
        locks.verify(entityManager).refresh(item, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void staleClaimAndReplacedItemCannotBecomeReady() {
        when(outbox.ownsClaim(event)).thenReturn(false);
        service.apply(work(), response(), "audio/wav");
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.TTS_PENDING);
        when(outbox.ownsClaim(event)).thenReturn(true);
        item.markReplaced();
        service.apply(work(), response(), "audio/wav");
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.REPLACED);
    }

    @Test
    void exhaustedAudioSchedulesIndependentReplacementAndPreservesPendingItems() {
        when(outbox.fail(eq(event), anyString(), eq(false), any(), anyInt(), any()))
                .thenReturn(new ListeningOutboxTransactionService.FailureResult(true, 1));
        when(items.countLogicalItemsByStatus(20L, ListeningItemStatus.TTS_PENDING)).thenReturn(1L);
        service.recordFailure(event, "tts failed", false, Duration.ZERO);
        verify(enqueue).enqueue(ListeningOutboxType.GENERATE_SET, 20L,
                new ListeningGenerationCommand(100L, 1, 1, 0),
                "listening:set:20:replace:1:1:tts-manual:0");
        assertThat(set.getStatus()).isEqualTo(ListeningDailySetStatus.PARTIAL);
    }

    @Test
    void replacementCapacityReservesAllMissingOriginalSlots() {
        when(policy.getHardItemLimit()).thenReturn(5);
        when(outbox.fail(eq(event), anyString(), eq(false), any(), anyInt(), any()))
                .thenReturn(new ListeningOutboxTransactionService.FailureResult(true, 1));
        service.recordFailure(event, "tts failed", false, Duration.ZERO);
        verifyNoInteractions(enqueue);
        assertThat(set.getFailureReason()).isEqualTo("tts failed");
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.NOT_EVALUABLE);
    }

    @Test
    void orphanWithMissingOutboxGetsRecoverableEvent() {
        service.recoverOrphan(100L);
        verify(enqueue).enqueue(ListeningOutboxType.GENERATE_TTS, 100L, null,
                "listening:item:100:tts:recovery:missing");
    }

    @Test
    void activeAudioEventIsNotDuplicatedDuringRecovery() {
        var pending = ListeningOutboxEvent.create(ListeningOutboxType.GENERATE_TTS,
                100L, "{}", "tts", LocalDateTime.now());
        when(events.findFirstByEventTypeAndAggregateIdOrderByIdDesc(
                ListeningOutboxType.GENERATE_TTS, 100L)).thenReturn(Optional.of(pending));
        service.recoverOrphan(100L);
        verifyNoInteractions(enqueue);
    }

    private ListeningTtsTransactionService.TtsWork work() {
        return new ListeningTtsTransactionService.TtsWork(event,
                mock(AiListeningContract.TtsRequest.class), "key.wav", 60, 10000, 7);
    }

    private AiListeningContract.TtsResponse response() {
        var response = mock(AiListeningContract.TtsResponse.class);
        var audio = mock(AiListeningContract.Audio.class);
        when(response.audio()).thenReturn(audio);
        when(audio.durationMs()).thenReturn(5000);
        return response;
    }
}
