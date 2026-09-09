package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.common.enums.*;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.*;
import jp.co.translacat.domain.languagelearning.listening.daily.model.ListeningGenerationCommand;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.*;
import jp.co.translacat.domain.languagelearning.listening.outbox.entity.ListeningOutboxEvent;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.*;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.policy.LanguageComplexityPolicy;
import jp.co.translacat.domain.languagelearning.quality.repository.LanguageLearningGenerationFingerprintRepository;
import jp.co.translacat.domain.languagelearning.quality.service.*;
import jp.co.translacat.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListeningProgressiveGenerationTest {
    private final ListeningDailySetRepository sets = mock(ListeningDailySetRepository.class);
    private final ListeningItemRepository items = mock(ListeningItemRepository.class);
    private final ListeningPolicySettingQueryService policies = mock(ListeningPolicySettingQueryService.class);
    private final ListeningPolicySetting policy = mock(ListeningPolicySetting.class);
    private final ListeningOutboxCommandService enqueue = mock(ListeningOutboxCommandService.class);
    private final ListeningOutboxTransactionService outbox = mock(ListeningOutboxTransactionService.class);
    private final ListeningOutboxEventRepository events = mock(ListeningOutboxEventRepository.class);
    private final LanguageLearningJsonCodec json = mock(LanguageLearningJsonCodec.class);
    private final GenerationDiversityContextService diversity = mock(GenerationDiversityContextService.class);
    private final Set<Integer> savedIndices = new HashSet<>();
    private ListeningDailySet set;
    private ListeningGenerationTransactionService service;
    private ListeningOutboxTransactionService.ClaimedEvent event;

    @BeforeEach
    void setup() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        set = ListeningDailySet.create(user, LocalDate.now(), "ko", "ja",
                ListeningLearningMode.DICTATION, ListeningDifficulty.MY_LEVEL,
                "{}", "[]", "{}", "policy", 5);
        ReflectionTestUtils.setField(set, "id", 20L);
        when(sets.findLockedById(20L)).thenReturn(Optional.of(set));
        when(sets.findById(20L)).thenReturn(Optional.of(set));
        when(policies.get()).thenReturn(policy);
        when(policy.getHardItemLimit()).thenReturn(10);
        when(policy.getReferenceAudioMaxSeconds()).thenReturn(60);
        when(policy.getManualRetryLimit()).thenReturn(2);
        when(json.write(any())).thenReturn("{}");
        when(json.read(eq("[]"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(List.of());
        when(diversity.context(anyLong(), anyString(), any())).thenReturn(DiversityContext.empty());
        when(items.existsByDailySetIdAndItemIndex(eq(20L), anyInt()))
                .thenAnswer(call -> savedIndices.contains(call.getArgument(1)));
        when(items.saveAndFlush(any(ListeningItem.class))).thenAnswer(call -> {
            ListeningItem item = call.getArgument(0);
            savedIndices.add(item.getItemIndex());
            ReflectionTestUtils.setField(item, "id", 100L + item.getItemIndex());
            return item;
        });
        service = new ListeningGenerationTransactionService(sets, items, policies,
                enqueue, outbox, json, diversity, mock(GenerationFingerprintCommandService.class),
                mock(LanguageLearningGenerationFingerprintRepository.class), new LanguageComplexityPolicy());
        event = new ListeningOutboxTransactionService.ClaimedEvent(30L,
                ListeningOutboxType.GENERATE_SET, 20L, "payload", "key", 1);
        when(outbox.ownsClaim(event)).thenReturn(true);
    }

    @Test
    void asksAiForOneLocalItemWhileCommandOwnsGlobalSlot() {
        when(json.read("payload", ListeningGenerationCommand.class))
                .thenReturn(ListeningGenerationCommand.item(3, 0));
        var work = service.prepare(event);
        assertThat(work.expectedCount()).isEqualTo(1);
        assertThat(work.request().setContext().itemCount()).isEqualTo(1);
        assertThat(work.command().logicalItemIndex()).isEqualTo(3);
    }

    @Test
    void atomicallyPersistsGlobalSlotThenSchedulesAudioAndNextSlot() {
        savedIndices.add(1);
        set.recordPhysicalItem();
        service.apply(work(2), response());
        var captured = org.mockito.ArgumentCaptor.forClass(ListeningItem.class);
        verify(items).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getItemIndex()).isEqualTo(2);
        verify(enqueue).enqueue(ListeningOutboxType.GENERATE_TTS, 102L, null,
                "listening:item:102:tts:0");
        verify(enqueue).enqueue(ListeningOutboxType.GENERATE_SET, 20L,
                ListeningGenerationCommand.item(3, 0), "listening:set:20:generate:item:3:manual:0");
        verify(outbox).succeed(eq(30L), any(LocalDateTime.class));
        assertThat(set.getPhysicalItemCount()).isEqualTo(2);
    }

    @Test
    void duplicateSlotDoesNotInsertAndRepairsMissingContinuation() {
        savedIndices.add(1);
        service.apply(work(1), response());
        verify(items, never()).saveAndFlush(any());
        verify(enqueue).enqueue(ListeningOutboxType.GENERATE_SET, 20L,
                ListeningGenerationCommand.item(2, 0), "listening:set:20:generate:item:2:manual:0");
    }

    @Test
    void staleClaimCannotApplyOrPoisonNewerGeneration() {
        when(outbox.ownsClaim(event)).thenReturn(false);
        service.apply(work(1), response());
        service.recordFailure(event, "late failure", false, Duration.ZERO);
        verify(items, never()).saveAndFlush(any());
        verifyNoInteractions(enqueue);
        assertThat(set.getFailureReason()).isNull();
    }

    @Test
    void generationFailurePreservesEarlierItemsAndPendingAudio() {
        savedIndices.addAll(List.of(1, 2));
        set.recordPhysicalItem();
        set.recordPhysicalItem();
        when(json.read("payload", ListeningGenerationCommand.class))
                .thenReturn(ListeningGenerationCommand.item(3, 0));
        when(outbox.fail(eq(event), anyString(), eq(false), any(), anyInt(), any()))
                .thenReturn(new ListeningOutboxTransactionService.FailureResult(true, 1));
        when(items.countLogicalItemsByStatus(20L, ListeningItemStatus.TTS_PENDING)).thenReturn(2L);
        service.recordFailure(event, "slot 3 failed", false, Duration.ZERO);
        assertThat(set.getStatus()).isEqualTo(ListeningDailySetStatus.PARTIAL);
        assertThat(set.getFailureReason()).isEqualTo("slot 3 failed");
        assertThat(set.getPhysicalItemCount()).isEqualTo(2);
    }

    @Test
    void manualRetryResumesFirstHoleWithoutDeletingExistingItems() {
        savedIndices.addAll(List.of(1, 2, 4));
        for (int ignored : savedIndices) set.recordPhysicalItem();
        set.fail("slot 3 failed");
        when(enqueue.enqueue(eq(ListeningOutboxType.GENERATE_SET), eq(20L), any(), anyString()))
                .thenReturn(ListeningOutboxEvent.create(ListeningOutboxType.GENERATE_SET,
                        20L, "{}", "retry", LocalDateTime.now()));
        new ListeningGenerationRetryCommandService(sets, items, policies, enqueue, events)
                .retry(10L, 20L);
        verify(enqueue).enqueue(ListeningOutboxType.GENERATE_SET, 20L,
                ListeningGenerationCommand.item(3, 1), "listening:set:20:generate:item:3:manual:1");
        assertThat(set.getPhysicalItemCount()).isEqualTo(3);
        assertThat(set.getFailureReason()).isNull();
        verify(items, never()).deleteAll();
    }

    private ListeningGenerationTransactionService.GenerationWork work(int index) {
        return new ListeningGenerationTransactionService.GenerationWork(event,
                ListeningGenerationCommand.item(index, 0), null, 1, 60);
    }

    private AiListeningContract.GenerationResponse response() {
        var item = mock(AiListeningContract.GeneratedItem.class);
        when(item.itemIndex()).thenReturn(1);
        when(item.contentHash()).thenReturn("hash");
        var response = mock(AiListeningContract.GenerationResponse.class);
        when(response.items()).thenReturn(List.of(item));
        when(response.generationVersion()).thenReturn("generator");
        return response;
    }
}
