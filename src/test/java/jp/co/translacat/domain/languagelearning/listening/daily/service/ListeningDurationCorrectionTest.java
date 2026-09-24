package jp.co.translacat.domain.languagelearning.listening.daily.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.audio.service.ListeningAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.listening.common.enums.*;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.*;
import jp.co.translacat.domain.languagelearning.listening.daily.model.*;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.*;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.*;
import jp.co.translacat.domain.languagelearning.listening.setting.model.ListeningPolicySnapshot;
import jp.co.translacat.domain.languagelearning.listening.setting.port.ListeningPolicyGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListeningDurationCorrectionTest {
    private final ListeningItemRepository items = mock(ListeningItemRepository.class);
    private final ListeningDailySetRepository sets = mock(ListeningDailySetRepository.class);
    private final ListeningPolicyGateway policies = mock(ListeningPolicyGateway.class);
    private final ListeningPolicySnapshot policy = mock(ListeningPolicySnapshot.class);
    private final ListeningOutboxCommandService enqueue = mock(ListeningOutboxCommandService.class);
    private final ListeningOutboxTransactionService outbox = mock(ListeningOutboxTransactionService.class);
    private final ListeningOutboxEventRepository events = mock(ListeningOutboxEventRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final LanguageLearningJsonCodec json = new LanguageLearningJsonCodec(new ObjectMapper().findAndRegisterModules());
    private final AiListeningContract.DurationDemand demand = ListeningDurationPolicy.effective(ListeningDifficulty.MY_LEVEL, 1.0, 30.0);
    private ListeningDailySet set;
    private ListeningItem item;
    private ListeningOutboxTransactionService.ClaimedEvent event;

    @BeforeEach
    void setup() {
        set = ListeningDailySet.create(null, LocalDate.now(), "ko", "ja", ListeningLearningMode.DICTATION,
                ListeningDifficulty.MY_LEVEL, "{}", "[]", "{}", "policy", 5);
        ReflectionTestUtils.setField(set, "id", 20L);
        set.recordPhysicalItem();
        var generated = new AiListeningContract.GeneratedItem(1, "original", "original", List.of("a", "b"),
                List.of("point"), List.of(), 9, "hash", "similarity", new AiListeningContract.Safety(true, List.of()));
        generated = ListeningDurationPolicy.withDuration(generated, demand, 0);
        item = ListeningItem.create(set, 1, "original", "original", "[\"a\",\"b\"]", "[\"point\"]", "[]",
                9, "hash", "similarity", json.write(generated), null, 0);
        ReflectionTestUtils.setField(item, "id", 100L);
        when(items.findById(100L)).thenReturn(Optional.of(item));
        when(items.findLockedById(100L)).thenReturn(Optional.of(item));
        when(sets.findLockedById(20L)).thenReturn(Optional.of(set));
        when(policies.get()).thenReturn(policy);
        when(policy.getHardItemLimit()).thenReturn(10);
        when(policy.getReferenceAudioMaxSeconds()).thenReturn(30);
        when(items.existsByDailySetIdAndItemIndex(20L, 1)).thenReturn(true);
        event = new ListeningOutboxTransactionService.ClaimedEvent(30L, ListeningOutboxType.GENERATE_TTS,
                100L, "{}", "key", 1);
        when(outbox.ownsClaim(event)).thenReturn(true);
    }

    @Test
    void correctionBudgetAndOriginalContentSurviveServiceRestart() {
        var service = service();
        service.recordDurationFailure(work(), 4.5, "AUDIO_TOO_SHORT");
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.NOT_EVALUABLE);
        assertThat(item.getSourceText()).isEqualTo("original");
        assertThat(item.getAudioObjectKey()).isNull();
        var persisted = json.read(item.getGenerationMetadataJson(), AiListeningContract.GeneratedItem.class);
        assertThat(persisted.qualityCorrectionCount()).isEqualTo(1);
        var command = ArgumentCaptor.forClass(Object.class);
        verify(enqueue).enqueue(eq(ListeningOutboxType.GENERATE_SET), eq(20L), command.capture(), anyString());
        var restored = json.read(json.write(command.getValue()), ListeningGenerationCommand.class);
        assertThat(restored.durationCorrection().previousMeasuredSeconds()).isEqualTo(4.5);
        assertThat(restored.durationCorrection().qualityCorrectionCount()).isEqualTo(1);
        assertThatThrownBy(() -> item.startManualTtsRetry(1)).isInstanceOf(IllegalStateException.class);
        // Restart/replayed delivery cannot enqueue a second correction.
        service().recordDurationFailure(work(), 4.5, "AUDIO_TOO_SHORT");
        verify(enqueue, times(1)).enqueue(any(), anyLong(), any(), anyString());
    }

    @Test
    void correctedVersionFailureIsTerminalWithoutThirdVersion() {
        var metadata = json.read(item.getGenerationMetadataJson(), AiListeningContract.GeneratedItem.class);
        item.reserveDurationCorrection(json.write(ListeningDurationPolicy.withDuration(metadata, demand, 1)));
        service().recordDurationFailure(work(), 4.0, "AUDIO_TOO_SHORT");
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.NOT_EVALUABLE);
        assertThat(set.getFailureReason()).isEqualTo("AUDIO_TOO_SHORT");
        verifyNoInteractions(enqueue);
    }

    @Test
    void staleClaimOrAlreadyPublishedAudioCannotCorrectContent() {
        when(outbox.ownsClaim(event)).thenReturn(false);
        service().recordDurationFailure(work(), 4.0, "AUDIO_TOO_SHORT");
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.TTS_PENDING);
        when(outbox.ownsClaim(event)).thenReturn(true);
        item.markTtsReady("published.wav", 9000, "audio/wav", "checksum", "{}", LocalDateTime.now().plusDays(1));
        service().recordDurationFailure(work(), 4.0, "AUDIO_TOO_SHORT");
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.READY);
        assertThat(item.getAudioObjectKey()).isEqualTo("published.wav");
        verifyNoInteractions(enqueue);
    }

    @Test
    void lateContentHashCannotPublishAudioForDifferentSource() {
        var request = new AiListeningContract.TtsRequest("request", "key", 100L, "different", "wrong-hash",
                "generation", "ja", null, "NORMAL", "policy", "model", 2, 0, demand);
        var work = new ListeningTtsTransactionService.TtsWork(event, request, "late.wav", 30, 100000, 7);
        service().apply(work, mock(AiListeningContract.TtsResponse.class), "audio/wav");
        assertThat(item.getAudioObjectKey()).isNull();
        assertThat(item.getSourceText()).isEqualTo("original");
    }

    @Test
    void infrastructureFailureAfterCorrectionCannotCreateAnotherContentVersion() {
        var metadata = json.read(item.getGenerationMetadataJson(), AiListeningContract.GeneratedItem.class);
        item.reserveDurationCorrection(json.write(ListeningDurationPolicy.withDuration(metadata, demand, 1)));
        when(outbox.fail(eq(event), anyString(), eq(false), any(), anyInt(), any()))
                .thenReturn(new ListeningOutboxTransactionService.FailureResult(true, 1));
        service().recordFailure(event, "TTS_FAILED", false, Duration.ZERO);
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.NOT_EVALUABLE);
        verifyNoInteractions(enqueue);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROVIDER_RATE_LIMITED", "PROVIDER_UNAVAILABLE", "PROVIDER_TIMEOUT"})
    void exhaustedInfrastructureFailurePreservesSourceForManualTtsRetry(String code) {
        String originalMetadata = item.getGenerationMetadataJson();
        when(outbox.fail(eq(event), eq(code), eq(true), any(), anyInt(), any()))
                .thenReturn(new ListeningOutboxTransactionService.FailureResult(true, 3));
        service().recordFailure(event, "provider diagnostic must not persist", true,
                Duration.ofSeconds(36661), code);
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.NOT_EVALUABLE);
        assertThat(item.getFailureReason()).isEqualTo(code);
        assertThat(set.getFailureReason()).isEqualTo(code);
        assertThat(item.getSourceText()).isEqualTo("original");
        assertThat(item.getContentHash()).isEqualTo("hash");
        assertThat(item.getGenerationMetadataJson()).isEqualTo(originalMetadata);
        assertThat(item.getReplacementSequence()).isZero();
        verifyNoInteractions(enqueue);
        // Existing manual TTS retry remains possible and retains this exact source.
        item.startManualTtsRetry(1);
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.TTS_PENDING);
        assertThat(item.getSourceText()).isEqualTo("original");
    }

    @Test
    void infrastructureCooldownUsesExistingDurableRetryWithoutSourceRegeneration() {
        String code = "PROVIDER_RATE_LIMITED";
        Duration delay = Duration.ofSeconds(36661);
        when(outbox.fail(eq(event), eq(code), eq(true), eq(delay), anyInt(), any()))
                .thenReturn(new ListeningOutboxTransactionService.FailureResult(false, 1));
        service().recordFailure(event, "raw provider diagnostic", true, delay, code);
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.TTS_PENDING);
        assertThat(item.getAutomaticTtsRetryCount()).isEqualTo(1);
        assertThat(item.getFailureReason()).isEqualTo(code);
        assertThat(item.getSourceText()).isEqualTo("original");
        verifyNoInteractions(enqueue);
    }

    @Test
    void legacyFailureStillPermitsItsFirstReplacement() {
        when(outbox.fail(eq(event), anyString(), eq(false), any(), anyInt(), any()))
                .thenReturn(new ListeningOutboxTransactionService.FailureResult(true, 1));
        service().recordFailure(event, "TTS_FAILED", false, Duration.ZERO);
        var command = ArgumentCaptor.forClass(Object.class);
        verify(enqueue).enqueue(eq(ListeningOutboxType.GENERATE_SET), eq(20L), command.capture(), anyString());
        var replacement = (ListeningGenerationCommand) command.getValue();
        assertThat(replacement.replacementSequence()).isEqualTo(1);
        assertThat(replacement.durationCorrection()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 9})
    void persistedReplacementLineageCannotCreateThirdSourceAfterRestart(int sequence) {
        ReflectionTestUtils.setField(item, "replacementSequence", sequence);
        when(outbox.fail(eq(event), anyString(), eq(false), any(), anyInt(), any()))
                .thenReturn(new ListeningOutboxTransactionService.FailureResult(true, 1));
        service().recordFailure(event, "TTS_FAILED", false, Duration.ZERO);
        assertThat(item.getStatus()).isEqualTo(ListeningItemStatus.NOT_EVALUABLE);
        assertThat(item.getSourceText()).isEqualTo("original");
        assertThat(set.getFailureReason()).isEqualTo("TTS_FAILED");
        // A new service instance and the existing manual retry cannot reset lineage.
        item.startManualTtsRetry(1);
        service().recordFailure(event, "TTS_FAILED", false, Duration.ZERO);
        verifyNoInteractions(enqueue);
    }

    private ListeningTtsTransactionService service() {
        return new ListeningTtsTransactionService(items, sets, policies, new ListeningAudioKeyFactory(),
                enqueue, outbox, json, entityManager, events);
    }

    private ListeningTtsTransactionService.TtsWork work() {
        var request = new AiListeningContract.TtsRequest("request", "key", 100L, item.getSourceText(), item.getContentHash(),
                "generation", "ja", null, "NORMAL", "policy", "model", 2, 0, demand);
        return new ListeningTtsTransactionService.TtsWork(event, request, "reference.wav", 30, 1000000, 7);
    }
}
