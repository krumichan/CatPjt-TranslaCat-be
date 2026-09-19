package jp.co.translacat.domain.languagelearning.practice.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PersonalizedVocabularyPlanDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeGeneratedQuestionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeOptionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeReviewTargetDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.VocabularyPlanItemDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeQuestionType;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.exception.AiServerCommunicationException;
import jp.co.translacat.global.exception.AiServerFailureCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

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
    void contextualChoicePersistsPlanBeforePublishingFirstQuestion() {
        var request = contextualRequest(null);
        var claim = new PracticePersistenceService.GenerationClaim(12L, 1, "token", 0, request);
        var planResponse = contextualPlanResponse();
        var response = contextualResponse("contextual-request-context", vocabularyPlan());
        when(persistence.claim(eq(12L), any(), any(), eq(3))).thenReturn(Optional.of(claim));
        when(aiClient.generatePractice(any())).thenReturn(planResponse, response);
        when(persistence.persistVocabularyPlan(claim, vocabularyPlan())).thenReturn(true);

        worker.generateNext(12L);

        var ordered = inOrder(aiClient, persistence);
        ordered.verify(aiClient).generatePractice(argThat(planRequest ->
                planRequest.vocabularyPlanOnly() && planRequest.vocabularyPlan() == null));
        ordered.verify(persistence).persistVocabularyPlan(claim, vocabularyPlan());
        ordered.verify(aiClient).generatePractice(argThat(contextRequest ->
                !contextRequest.vocabularyPlanOnly()
                        && contextRequest.vocabularyPlan().equals(vocabularyPlan())
                        && contextRequest.requestId().endsWith("-context")));
        ordered.verify(persistence).append(claim, response);
    }

    @Test
    void firstItemLogsSafePlanAndJitStagesAroundAiFailure() {
        var request = contextualRequest(null);
        var claim = new PracticePersistenceService.GenerationClaim(12L, 1, "token", 0, request);
        when(persistence.claim(eq(12L), any(), any(), eq(3))).thenReturn(Optional.of(claim));
        when(persistence.persistVocabularyPlan(claim, vocabularyPlan())).thenReturn(true);
        when(aiClient.generatePractice(any())).thenReturn(contextualPlanResponse())
                .thenThrow(new AiServerCommunicationException(
                        "raw learner body", AiServerFailureCode.HTTP_4XX, 422,
                        "code=AI_CONTENT_QUALITY_REJECTED stage=LEXICAL_VALIDATION message=redacted",
                        new RuntimeException("secret")
                ));
        Logger logger = (Logger) LoggerFactory.getLogger(PracticeGenerationWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            worker.generateNext(12L);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        verify(persistence).persistVocabularyPlan(claim, vocabularyPlan());
        verify(persistence).fail(claim, "HTTP_4XX");
        verify(persistence, never()).append(any(), any());
        String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        org.assertj.core.api.Assertions.assertThat(logged).contains(
                "stage=PLAN_REQUEST_STARTED", "stage=PLAN_RESPONSE_RECEIVED",
                "stage=PLAN_PERSISTED", "stage=LEXICAL_VALIDATION_REQUEST_STARTED",
                "stage=CONTEXT_REQUEST_STARTED", "stage=GENERATION_FAILED",
                "failedStage=CONTEXT_REQUEST_STARTED", "stage=LEXICAL_VALIDATION message=redacted",
                "httpStatus=422", "failureCode=HTTP_4XX", "retryable=false"
        ).doesNotContain("追加の検証期間", "raw learner body", "secret");
    }

    @Test
    void staleVocabularyPlanClaimCannotPublishQuestion() {
        var request = contextualRequest(null);
        var claim = new PracticePersistenceService.GenerationClaim(12L, 1, "stale", 0, request);
        when(persistence.claim(eq(12L), any(), any(), eq(3))).thenReturn(Optional.of(claim));
        when(aiClient.generatePractice(any())).thenReturn(contextualPlanResponse());
        when(persistence.persistVocabularyPlan(claim, vocabularyPlan())).thenReturn(false);

        worker.generateNext(12L);

        verify(persistence, never()).append(any(), any());
    }

    @Test
    void validFirstPlanIsPersistedEvenWhenQuestionContractIsRejected() {
        var request = contextualRequest(null);
        var claim = new PracticePersistenceService.GenerationClaim(12L, 1, "token", 0, request);
        var invalidQuestionResponse = new AiPracticeGenerationResponseDto(
                "contextual-request-context",
                "vocabulary-contextual-choice-recipe-v3",
                PracticeDomain.VOCABULARY,
                "CONTEXTUAL_CHOICE",
                4,
                List.of(),
                vocabularyPlan()
        );
        when(persistence.claim(eq(12L), any(), any(), eq(3))).thenReturn(Optional.of(claim));
        when(aiClient.generatePractice(any())).thenReturn(
                contextualPlanResponse(), invalidQuestionResponse
        );
        when(persistence.persistVocabularyPlan(claim, vocabularyPlan())).thenReturn(true);

        worker.generateNext(12L);

        var ordered = inOrder(persistence);
        ordered.verify(persistence).persistVocabularyPlan(claim, vocabularyPlan());
        ordered.verify(persistence).fail(claim, "AI_SCHEMA_INVALID");
        verify(persistence, never()).append(any(), any());
    }

    @Test
    void contextualChoiceRequiresValidImmutableDailyPlan() {
        var firstRequest = contextualRequest(null);
        assertThatThrownBy(() -> PracticeGenerationWorker.validateGenerated(
                firstRequest, contextualResponse(null)
        )).isInstanceOf(BusinessException.class);

        var plan = vocabularyPlan();
        var invalidItems = new java.util.ArrayList<>(plan.items());
        VocabularyPlanItemDto first = invalidItems.getFirst();
        invalidItems.set(0, new VocabularyPlanItemDto(
                first.globalOrder(), first.reviewTarget(), first.targetExpression(),
                first.canonicalKey(), first.distractors(), first.skillTag(),
                PracticeDifficulty.EASIER, 3, first.scenarioFamily(),
                first.anchorType(), first.anchorValue()
        ));
        var invalidMix = new PersonalizedVocabularyPlanDto(plan.version(), List.copyOf(invalidItems));
        assertThatThrownBy(() -> PracticeGenerationWorker.validateGenerated(
                firstRequest, contextualResponse(invalidMix)
        )).isInstanceOf(BusinessException.class);

        var retryRequest = contextualRequest(plan);
        var replacement = new PersonalizedVocabularyPlanDto("replacement", plan.items());
        assertThatThrownBy(() -> PracticeGenerationWorker.validateGenerated(
                retryRequest, contextualResponse(replacement)
        )).isInstanceOf(BusinessException.class);
        PracticeGenerationWorker.validateGenerated(retryRequest, contextualResponse(plan));
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
    void terminalAiFailurePreservesSafeClassificationAndLogsContractContext() {
        var claim = claim(2, 0);
        when(persistence.claim(eq(12L), any(), any(), eq(3))).thenReturn(Optional.of(claim));
        when(aiClient.generatePractice(request())).thenThrow(
                new AiServerCommunicationException(
                        "response body must not be persisted",
                        AiServerFailureCode.HTTP_4XX,
                        422,
                        "type=missing loc=body.previousQuestions.0.options msg=Field required",
                        new RuntimeException("private response")
                )
        );

        Logger logger = (Logger) LoggerFactory.getLogger(PracticeGenerationWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            worker.generateNext(12L);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        verify(persistence).fail(claim, "HTTP_4XX");
        verify(persistence, never()).recordInfrastructureFailure(any(), any(), any(), anyInt());
        String logOutput = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        org.assertj.core.api.Assertions.assertThat(logOutput)
                .contains(
                        "setId=12",
                        "order=2",
                        "requestId=request",
                        "endpoint=/api/v1/language-learning/practice/generate",
                        "httpStatus=422",
                        "safeDetail=type=missing loc=body.previousQuestions.0.options msg=Field required",
                        "previousQuestions=0",
                        "questionOffset=0",
                        "failureCode=HTTP_4XX",
                        "retryable=false"
                )
                .doesNotContain("private response", "response body must not be persisted");
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

    private AiPracticeGenerationRequestDto contextualRequest(
            PersonalizedVocabularyPlanDto plan
    ) {
        return new AiPracticeGenerationRequestDto(
                "contextual-request",
                PracticeDomain.VOCABULARY,
                "CONTEXTUAL_CHOICE",
                "ko",
                "ja",
                1,
                4,
                0,
                1,
                0,
                List.of("業務"),
                List.of(),
                List.of(),
                List.of(
                        new PracticeReviewTargetDto(
                                "追加の検証期間", "追加の検証期間", 70.0, 2,
                                List.of(PracticeQuestionType.SINGLE_CHOICE), "MEANING"
                        ),
                        new PracticeReviewTargetDto(
                                "顧客サービスへの影響", "顧客サービスへの影響", 65.0, 1,
                                List.of(PracticeQuestionType.SINGLE_CHOICE), "NUANCE"
                        )
                ),
                1,
                LocalDate.of(2026, 9, 14),
                List.of(),
                plan
        );
    }

    private AiPracticeGenerationResponseDto contextualResponse(
            PersonalizedVocabularyPlanDto plan
    ) {
        return contextualResponse("contextual-request", plan);
    }

    private AiPracticeGenerationResponseDto contextualResponse(
            String requestId,
            PersonalizedVocabularyPlanDto plan
    ) {
        VocabularyPlanItemDto planned = plan == null ? null : plan.items().getFirst();
        String target = planned == null ? "追加の検証期間" : planned.targetExpression();
        List<String> distractors = planned == null
                ? List.of("関連候補1一", "関連候補1二", "関連候補1三")
                : planned.distractors();
        var question = new PracticeGeneratedQuestionDto(
                1,
                PracticeQuestionType.SINGLE_CHOICE,
                PracticeDifficulty.CURRENT,
                4,
                null,
                null,
                "状況を最も適切に表すものを選んでください。",
                List.of(
                        new PracticeOptionDto("A", target),
                        new PracticeOptionDto("B", distractors.get(0)),
                        new PracticeOptionDto("C", distractors.get(1)),
                        new PracticeOptionDto("D", distractors.get(2))
                ),
                List.of("A"),
                "MEANING",
                null,
                "문맥상 가장 적절한 표현입니다.",
                "文脈上、最も適切な表現です。",
                target,
                target,
                true,
                List.of()
        );
        return new AiPracticeGenerationResponseDto(
                requestId,
                "vocabulary-contextual-choice-recipe-v3",
                PracticeDomain.VOCABULARY,
                "CONTEXTUAL_CHOICE",
                4,
                List.of(question),
                plan
        );
    }

    private AiPracticeGenerationResponseDto contextualPlanResponse() {
        return new AiPracticeGenerationResponseDto(
                "contextual-request",
                "vocabulary-contextual-choice-recipe-v3",
                PracticeDomain.VOCABULARY,
                "CONTEXTUAL_CHOICE",
                4,
                List.of(),
                vocabularyPlan()
        );
    }
}
