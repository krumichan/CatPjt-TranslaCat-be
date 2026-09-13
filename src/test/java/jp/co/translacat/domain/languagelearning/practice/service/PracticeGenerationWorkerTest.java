package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.exception.AiServerCommunicationException;
import jp.co.translacat.global.exception.AiServerFailureCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static jp.co.translacat.domain.languagelearning.practice.service.PracticePersistenceServiceTest.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PracticeGenerationWorkerTest {
    @Mock private PracticePersistenceService persistence;
    @Mock private LanguageLearningAiClient aiClient;
    private PracticeGenerationWorker worker;

    @BeforeEach
    void setup() {
        worker = new PracticeGenerationWorker(persistence, aiClient, Runnable::run,
                1800, 3, 10, 120);
    }

    @Test
    void doesNotCallAiWhenAnotherWorkerOwnsClaim() {
        when(persistence.claim(eq(12L), any(), any(), eq(3))).thenReturn(Optional.empty());
        worker.generateNext(12L);
        verifyNoInteractions(aiClient);
    }

    @Test
    void publishesOneQuestionPerClaim() {
        var claim = claim(1, 0);
        when(persistence.claim(eq(12L), any(), any(), eq(3))).thenReturn(Optional.of(claim));
        when(aiClient.generatePractice(request())).thenReturn(response());
        worker.generateNext(12L);
        verify(persistence).append(claim, response());
        verify(persistence, never()).fail(any(), any());
    }

    @Test
    void remoteFailureIsRecordedWithoutReplacingExistingQuestions() {
        var claim = claim(3, 0);
        when(persistence.claim(eq(12L), any(), any(), eq(3))).thenReturn(Optional.of(claim));
        when(aiClient.generatePractice(request())).thenThrow(new IllegalStateException("AI unavailable"));
        worker.generateNext(12L);
        verify(persistence).fail(claim, "UNKNOWN");
        verify(persistence, never()).append(any(), any());
    }

    @Test
    void rejectsUnexpectedResponseCountOrRequestIdentity() {
        assertThatThrownBy(() -> PracticeGenerationWorker.validateGenerated(request(),
                new AiPracticeGenerationResponseDto("request", "practice", PracticeDomain.READING,
                        "COMPREHENSION", 3, List.of(item(1), item(2)))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PracticeGenerationWorker.validateGenerated(request(),
                new AiPracticeGenerationResponseDto("other", "practice", PracticeDomain.READING,
                        "COMPREHENSION", 3, List.of(item(1)))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void busyExecutorLeavesDurableWorkPendingForNextTick() {
        worker = new PracticeGenerationWorker(persistence, aiClient,
                task -> { throw new java.util.concurrent.RejectedExecutionException(); },
                1800, 3, 10, 120);
        when(persistence.pendingIds(any(), any())).thenReturn(List.of(12L));
        worker.dispatch();
        worker.dispatch();
        verify(persistence, times(2)).pendingIds(any(), any());
        verify(persistence, never()).claim(anyLong(), any(), any(), anyInt());
        verifyNoInteractions(aiClient);
    }

    @Test
    void transientInfrastructureFailuresAreDurablyDeferredWithSafeCodes() {
        for (AiServerFailureCode code : List.of(
                AiServerFailureCode.CIRCUIT_OPEN,
                AiServerFailureCode.CONNECT_FAILURE,
                AiServerFailureCode.CONNECT_TIMEOUT,
                AiServerFailureCode.HTTP_5XX
        )) {
            reset(persistence, aiClient);
            var claim = claim(2, 0);
            when(persistence.claim(eq(12L), any(), any(), eq(3))).thenReturn(Optional.of(claim));
            when(aiClient.generatePractice(request())).thenThrow(
                    new AiServerCommunicationException("safe", code, new RuntimeException())
            );

            worker.generateNext(12L);

            verify(persistence).recordInfrastructureFailure(
                    eq(claim), eq(code.name()), any(LocalDateTime.class), eq(3)
            );
            verify(persistence, never()).fail(any(), any());
        }
    }

    @Test
    void terminalAiFailurePreservesSafeClassification() {
        var claim = claim(2, 0);
        when(persistence.claim(eq(12L), any(), any(), eq(3))).thenReturn(Optional.of(claim));
        when(aiClient.generatePractice(request())).thenThrow(
                new AiServerCommunicationException(
                        "response body must not be persisted",
                        AiServerFailureCode.HTTP_4XX,
                        new RuntimeException("private response")
                )
        );

        worker.generateNext(12L);

        verify(persistence).fail(claim, "HTTP_4XX");
        verify(persistence, never()).recordInfrastructureFailure(any(), any(), any(), anyInt());
    }

    @Test
    void deferredClaimCanLaterReachAiAndAppendExactlyOnce() {
        var first = claim(2, 0);
        var retry = claim(2, 1);
        when(persistence.claim(eq(12L), any(), any(), eq(3)))
                .thenReturn(Optional.of(first), Optional.of(retry));
        when(aiClient.generatePractice(request()))
                .thenThrow(new AiServerCommunicationException(
                        "open", AiServerFailureCode.CIRCUIT_OPEN, new RuntimeException()))
                .thenReturn(response());

        worker.generateNext(12L);
        worker.generateNext(12L);

        verify(aiClient, times(2)).generatePractice(request());
        verify(persistence).recordInfrastructureFailure(
                eq(first), eq("CIRCUIT_OPEN"), any(LocalDateTime.class), eq(3)
        );
        verify(persistence).append(retry, response());
    }

    @Test
    void retryBackoffIsBounded() {
        org.assertj.core.api.Assertions.assertThat(worker.retryDelay(0)).isEqualTo(java.time.Duration.ofSeconds(10));
        org.assertj.core.api.Assertions.assertThat(worker.retryDelay(1)).isEqualTo(java.time.Duration.ofSeconds(20));
        org.assertj.core.api.Assertions.assertThat(worker.retryDelay(8)).isEqualTo(java.time.Duration.ofSeconds(120));
    }

    private PracticePersistenceService.GenerationClaim claim(int order, int retryCount) {
        return new PracticePersistenceService.GenerationClaim(
                12L, order, "token-" + retryCount, retryCount, request()
        );
    }
}
