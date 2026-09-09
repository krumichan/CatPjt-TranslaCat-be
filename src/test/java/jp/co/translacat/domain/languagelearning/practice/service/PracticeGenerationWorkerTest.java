package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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
        worker = new PracticeGenerationWorker(persistence, aiClient, Runnable::run, 1800);
    }

    @Test
    void doesNotCallAiWhenAnotherWorkerOwnsClaim() {
        when(persistence.claim(eq(12L), any(), any())).thenReturn(Optional.empty());
        worker.generateNext(12L);
        verifyNoInteractions(aiClient);
    }

    @Test
    void publishesOneQuestionPerClaim() {
        var claim = new PracticePersistenceService.GenerationClaim(12L, 1, "token", request());
        when(persistence.claim(eq(12L), any(), any())).thenReturn(Optional.of(claim));
        when(aiClient.generatePractice(request())).thenReturn(response());
        worker.generateNext(12L);
        verify(persistence).append(claim, response());
        verify(persistence, never()).fail(any(), any());
    }

    @Test
    void remoteFailureIsRecordedWithoutReplacingExistingQuestions() {
        var claim = new PracticePersistenceService.GenerationClaim(12L, 3, "token", request());
        when(persistence.claim(eq(12L), any(), any())).thenReturn(Optional.of(claim));
        when(aiClient.generatePractice(request())).thenThrow(new IllegalStateException("AI unavailable"));
        worker.generateNext(12L);
        verify(persistence).fail(claim, "AI unavailable");
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
                task -> { throw new java.util.concurrent.RejectedExecutionException(); }, 1800);
        when(persistence.pendingIds(any())).thenReturn(List.of(12L));
        worker.dispatch();
        worker.dispatch();
        verify(persistence, times(2)).pendingIds(any());
        verify(persistence, never()).claim(anyLong(), any(), any());
        verifyNoInteractions(aiClient);
    }
}
