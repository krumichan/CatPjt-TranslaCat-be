package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.ai.port.ListeningAiClient;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.audio.validator.ListeningAudioValidator;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.support.ListeningAiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListeningTtsInfrastructureFailureTest {
    private final ListeningTtsTransactionService transactions = mock(ListeningTtsTransactionService.class);
    private final ListeningAiClient ai = mock(ListeningAiClient.class);
    private final ListeningAudioStoragePort storage = mock(ListeningAudioStoragePort.class);
    private final ListeningAudioValidator validator = mock(ListeningAudioValidator.class);
    private final ListeningOutboxTransactionService.ClaimedEvent event =
            new ListeningOutboxTransactionService.ClaimedEvent(11L, ListeningOutboxType.GENERATE_TTS,
                    20L, "{}", "key", 1);

    @ParameterizedTest
    @CsvSource({"36661.2,36662", "-2,1", "0,1", "NaN,1", "Infinity,1", "1000000,86400"})
    void failedResponsePreservesTypedRateLimitAndSafeDelay(double delay, long expectedSeconds) {
        var request = request();
        when(transactions.prepare(event)).thenReturn(work(request));
        var error = new AiListeningContract.AiError("PROVIDER_RATE_LIMITED", "TTS",
                "Safe provider failure", true, Map.of("retryAfterSeconds", delay));
        when(ai.synthesize(request)).thenReturn(new AiListeningContract.TtsResponse("request", 20L,
                "FAILED", "source", "hash", "version", null, error, Map.of()));
        worker().process(event);
        verify(transactions).recordFailure(event, "Safe provider failure", true,
                Duration.ofSeconds(expectedSeconds), "PROVIDER_RATE_LIMITED");
        verify(ai, times(1)).synthesize(request);
        verify(ai, never()).getAudio(anyString());
        verifyNoInteractions(storage, validator);
    }

    @Test
    void typedHttpInfrastructureFailureRetainsItsCodeAndDelay() {
        var request = request();
        when(transactions.prepare(event)).thenReturn(work(request));
        when(ai.synthesize(request)).thenThrow(new ListeningAiException("Safe timeout", "PROVIDER_TIMEOUT",
                "TTS", true, Duration.ofSeconds(10), 20L, null));
        worker().process(event);
        verify(transactions).recordFailure(event, "Safe timeout", true,
                Duration.ofSeconds(10), "PROVIDER_TIMEOUT");
        verifyNoInteractions(storage, validator);
    }

    @Test
    void untypedExceptionDoesNotInferCodeFromItsMessage() {
        var request = request();
        when(transactions.prepare(event)).thenReturn(work(request));
        when(ai.synthesize(request)).thenThrow(new IllegalStateException("PROVIDER_RATE_LIMITED"));
        worker().process(event);
        verify(transactions).recordFailure(event, "PROVIDER_RATE_LIMITED", false, Duration.ZERO, null);
        verifyNoInteractions(storage, validator);
    }

    private ListeningTtsWorker worker() {
        return new ListeningTtsWorker(transactions, ai, storage, validator);
    }

    private AiListeningContract.TtsRequest request() {
        return new AiListeningContract.TtsRequest("request", "key", 20L, "source", "hash", "version",
                "ja", new AiListeningContract.Voice("ja", "Kore", "current", "STANDARD"),
                "NORMAL", "policy", "model", 2, 0);
    }

    private ListeningTtsTransactionService.TtsWork work(AiListeningContract.TtsRequest request) {
        return new ListeningTtsTransactionService.TtsWork(event, request, "unused.wav", 30, 1000000, 7);
    }
}
