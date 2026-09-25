package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.activity.model.GrowthActivityDraft;
import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.ai.dto.model.WritingEvaluationScoresDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.factory.WritingEvaluationRequestFactory;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.model.WritingEvaluationRequestContext;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.daily.service.WritingEvaluationCommandService;
import jp.co.translacat.domain.languagelearning.daily.validator.WritingEvaluationResponseValidator;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthOperation;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthCommands;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileCommandService;
import jp.co.translacat.domain.languagelearning.resultjournal.model.LearningResultCaptured;
import jp.co.translacat.domain.languagelearning.setting.model.UserSettingsSnapshot;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingResultKind;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.policy.SpeakingEvaluationEligibilityPolicy;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationMetricRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.repository.SpeakingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.service.SpeakingEvaluationResultCommandService;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 평가 서비스의 실제 분기를 실행한다. Spring 트랜잭션 자체는 별도 JPA 테스트에서 검증한다.
 */
class EvaluationResultCaptureTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final LanguageLearningJsonCodec codec = new LanguageLearningJsonCodec(mapper);

    @Test
    void scoredSpeakingPublishesAfterProfileAndActivityAreUpdated() throws Exception {
        var f = new SpeakingFixture();
        var response = f.response(0.9);
        f.service.apply(f.session, response);
        var order = inOrder(f.activity, f.growth, f.session, f.events);
        order.verify(f.activity).markEvaluated(80, 0.9);
        order.verify(f.session).markEvaluated("test-v1");
        order.verify(f.growth).append(eq(123L), any(GrowthOperation.class));
        order.verify(f.events).publishEvent(any(LearningResultCaptured.class));
        var event = f.capture();
        var json = mapper.readTree(event.payloadJson());
        assertEquals("SPEAKING_SCORED", event.kind());
        assertEquals("51", event.referenceId());
        assertTrue(json.get("formal").booleanValue());
        assertEquals(0.9, json.get("activityWeight").doubleValue());
    }

    @Test
    void insufficientSpeakingHasNoProfileUpdateAndIsNotMarkedAsFormal() throws Exception {
        var f = new SpeakingFixture();
        f.service.apply(f.session, f.response(0.2));
        var operation = ArgumentCaptor.forClass(GrowthOperation.class);
        verify(f.growth).append(eq(123L), operation.capture());
        assertEquals(false, operation.getValue().payload().get("formal"));
        var event = f.capture();
        var json = mapper.readTree(event.payloadJson());
        assertEquals("SPEAKING_INSUFFICIENT", event.kind());
        assertFalse(json.get("formal").booleanValue());
        assertEquals(0.0, json.get("activityWeight").doubleValue());
    }

    @Test
    void freeCoachingCannotUseTheScoredPath() {
        var f = new SpeakingFixture();
        when(f.session.getResultKind()).thenReturn(SpeakingResultKind.SESSION_COACHING);
        assertThrows(IllegalArgumentException.class, () -> f.service.apply(f.session, f.response(0.9)));
        verifyNoInteractions(f.evaluations, f.activities, f.growth, f.events);
    }

    @Test
    void anAlreadyStoredSpeakingResultDoesNotPublishAgain() {
        var f = new SpeakingFixture();
        when(f.evaluation.getStatus()).thenReturn("EVALUATED");
        when(f.evaluations.findFirstBySessionIdOrderByEvaluatedAtDesc(51L)).thenReturn(Optional.of(f.evaluation));
        f.service.apply(f.session, f.response(0.9));
        verifyNoInteractions(f.activities, f.growth, f.events);
    }

    @Test
    void writingPublishesOnlyAfterTheExistingProfileUpdate() throws Exception {
        var f = new WritingFixture();
        f.execute();
        var order = inOrder(f.profile, f.events);
        order.verify(f.profile)
                .applyDailyEvaluation(123L, 21L, f.response, DailyWritingDifficulty.NORMAL, List.of(), f.day);
        order.verify(f.events).publishEvent(any(LearningResultCaptured.class));
        var capture = ArgumentCaptor.forClass(LearningResultCaptured.class);
        verify(f.events).publishEvent(capture.capture());
        var event = capture.getValue();
        assertEquals("WRITING_SCORED", event.kind());
        assertEquals(31L, mapper.readTree(event.payloadJson()).get("answerId").longValue());
    }

    @Test
    void failedWritingDoesNotPublishASuccessfulResult() {
        var f = new WritingFixture();
        when(f.ai.evaluate(null)).thenThrow(new IllegalStateException("test AI failure"));
        assertThrows(BusinessException.class, f::execute);
        verifyNoInteractions(f.profile, f.events);
    }

    private class SpeakingFixture {
        final SpeakingTurnRepository turns = mock(SpeakingTurnRepository.class);
        final SpeakingEvaluationRepository evaluations = mock(SpeakingEvaluationRepository.class);
        final SpeakingEvaluationMetricRepository metrics = mock(SpeakingEvaluationMetricRepository.class);
        final LearningActivityCommandService activities = mock(LearningActivityCommandService.class);
        final GrowthCommands growth = mock(GrowthCommands.class);
        final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        final SpeakingSession session = mock(SpeakingSession.class);
        final SpeakingEvaluation evaluation = mock(SpeakingEvaluation.class);
        final GrowthActivityDraft activity =
                spy(new GrowthActivityDraft(123L, LearningSource.SPEAKING, "51", LocalDate.of(2026, 9, 24), "연습", 60,
                        LocalDate.of(2026, 9, 24).atStartOfDay(), LocalDate.of(2026, 9, 24).atTime(0, 1)));
        final SpeakingEvaluationResultCommandService service = new SpeakingEvaluationResultCommandService(
                turns, evaluations, metrics, new SpeakingEvaluationEligibilityPolicy(), codec, events, growth,
                activities);

        SpeakingFixture() {
            var user = mock(User.class);
            when(user.getId()).thenReturn(123L);
            when(session.getUser()).thenReturn(user);
            when(session.getId()).thenReturn(51L);
            when(session.getResultKind()).thenReturn(SpeakingResultKind.SCORED_EVALUATION);
            when(session.getLearningDate()).thenReturn(LocalDate.of(2026, 9, 24));
            when(session.getOriginLanguage()).thenReturn("ko");
            when(session.getLearningLanguage()).thenReturn("ja");
            when(evaluations.findFirstBySessionIdOrderByEvaluatedAtDesc(51L)).thenReturn(Optional.empty());
            when(evaluations.save(any(SpeakingEvaluation.class))).thenReturn(evaluation);
            when(evaluation.getId()).thenReturn(61L);
            when(evaluation.getEvaluationVersion()).thenReturn("test-v1");
            when(turns.findAllBySessionIdOrderByTurnIndexAsc(51L)).thenReturn(List.of());
            lenient().when(activities.speakingResult(session)).thenReturn(activity);
        }

        AiSpeakingEvaluationResponseDto response(double confidence) {
            return new AiSpeakingEvaluationResponseDto("request", "51", "EVALUATED", 80, confidence,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                    "test-v1", "test-v1", "test-v1", null);
        }

        LearningResultCaptured capture() {
            var captor = ArgumentCaptor.forClass(LearningResultCaptured.class);
            verify(events).publishEvent(captor.capture());
            return captor.getValue();
        }
    }

    private class WritingFixture {
        final LanguageLearningAiClient ai = mock(LanguageLearningAiClient.class);
        final WritingEvaluationRepository evaluations = mock(WritingEvaluationRepository.class);
        final LearningProfileCommandService profile = mock(LearningProfileCommandService.class);
        final WritingEvaluationRequestFactory requests = mock(WritingEvaluationRequestFactory.class);
        final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        final WritingEvaluationCommandService service = new WritingEvaluationCommandService(ai, evaluations, profile,
                requests, new WritingEvaluationResponseValidator(), codec, events);
        final User user = mock(User.class);
        final WritingAnswer answer = mock(WritingAnswer.class);
        final WritingEvaluation evaluation = mock(WritingEvaluation.class);
        final DailyWritingItem item = mock(DailyWritingItem.class);
        final UserSettingsSnapshot settings = mock(UserSettingsSnapshot.class);
        final DailyWritingSnapshot snapshot = mock(DailyWritingSnapshot.class);
        final LocalDate day = LocalDate.of(2026, 9, 24);
        final AiWritingEvaluationResponseDto response = new AiWritingEvaluationResponseDto("request",
                new WritingEvaluationScoresDto(80, 80, 80, 80, 80, 80), List.of(), List.of(), List.of(),
                List.of("one", "two"), null, null, "test-v1", "test-v1", "test-v1");

        WritingFixture() {
            when(user.getId()).thenReturn(123L);
            when(answer.getId()).thenReturn(31L);
            when(answer.getDailyItem()).thenReturn(item);
            when(item.getId()).thenReturn(41L);
            when(item.getDifficulty()).thenReturn(DailyWritingDifficulty.NORMAL);
            when(evaluation.getId()).thenReturn(21L);
            when(evaluations.findByAnswerId(31L)).thenReturn(Optional.of(evaluation));
            when(settings.getOriginLanguage()).thenReturn("ko");
            when(settings.getLearningLanguage()).thenReturn("ja");
            when(requests.createDaily(answer, settings, snapshot, day)).thenReturn(
                    new WritingEvaluationRequestContext(null, List.of()));
            when(ai.evaluate(null)).thenReturn(response);
        }

        void execute() {
            service.evaluateDaily(user, answer, settings, snapshot, day);
        }
    }
}
