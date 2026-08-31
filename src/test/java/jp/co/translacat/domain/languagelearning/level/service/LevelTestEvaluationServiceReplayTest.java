package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestInternalAnswerKeyDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestEvaluation;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestEvaluationRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LevelTestEvaluationServiceReplayTest {

    @Mock
    private LanguageLearningAiClient aiClient;
    @Mock
    private LevelTestResponseRepository responseRepository;
    @Mock
    private LevelTestEvaluationRepository evaluationRepository;
    @Mock
    private LevelTestAnswerCommandService answerCommandService;
    @Mock
    private LevelTestProgressCommandService progressCommandService;
    @Mock
    private LevelTestEvaluationStateService stateService;
    @Mock
    private LevelTestAudioService audioService;
    @Mock
    private LanguageLearningJsonCodec jsonCodec;
    @Mock
    private LevelTestResponse response;
    @Mock
    private LevelTestItem item;
    @Mock
    private LevelTestSession session;
    @Mock
    private LevelTestEvaluation evaluation;
    @Mock
    private User user;

    private LevelTestEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new LevelTestEvaluationService(
                aiClient,
                responseRepository,
                evaluationRepository,
                answerCommandService,
                progressCommandService,
                stateService,
                audioService,
                jsonCodec
        );
        when(response.getId()).thenReturn(41L);
        when(response.getItem()).thenReturn(item);
    }

    @Test
    void bestAnswerChoiceUsesStoredDeterministicPartialScoreWithoutCallingAi() {
        when(item.getStatus()).thenReturn(LevelTestItemStatus.READY);
        when(item.getAnswerMode()).thenReturn(LevelTestAnswerMode.CHOICE);
        when(item.getInternalAnswerKeyJson()).thenReturn("answer-key-json");
        when(response.getSelectedOptionKey()).thenReturn("D");
        when(evaluationRepository.findByResponseId(41L)).thenReturn(Optional.empty());
        when(jsonCodec.read("answer-key-json", LevelTestInternalAnswerKeyDto.class))
                .thenReturn(new LevelTestInternalAnswerKeyDto(
                        "A",
                        List.of(),
                        "BEST_ANSWER",
                        Map.of("A", 100, "B", 0, "C", 10, "D", 30)
                ));
        var progress = new LevelTestProgressCommandService.ProgressResult(true, 30, null, false);
        when(progressCommandService.apply(any(), any())).thenReturn(progress);

        LevelTestProgressCommandService.ProgressResult result =
                service.evaluate(response, null, null, null);

        assertThat(result.score()).isEqualTo(30);
        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(
                LevelTestProgressCommandService.EvaluationPayload.class
        );
        verify(progressCommandService).apply(org.mockito.ArgumentMatchers.eq(41L), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().score()).isEqualTo(30);
        verifyNoInteractions(aiClient);
    }

    @Test
    void invalidAudioRetryRequiresANewRecordingInsteadOfReevaluatingSameAudio() {
        when(responseRepository.findByItemId(18L)).thenReturn(Optional.of(response));
        when(item.getAnswerMode()).thenReturn(LevelTestAnswerMode.AUDIO);
        when(item.getSession()).thenReturn(session);
        when(session.getId()).thenReturn(1L);
        when(session.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(7L);
        when(evaluationRepository.findByResponseId(41L)).thenReturn(Optional.of(evaluation));
        when(evaluation.getReasonCode()).thenReturn("INVALID_AUDIO");

        assertThatThrownBy(() -> service.retry(7L, 1L, 18L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("다시 녹음");

        verifyNoInteractions(answerCommandService, audioService, aiClient, progressCommandService);
    }

    @Test
    void replayReturnsExistingEvaluationWithoutCallingAiAgain() {
        when(item.getStatus()).thenReturn(LevelTestItemStatus.EVALUATED);
        when(item.getSession()).thenReturn(session);
        when(session.getStatus()).thenReturn(LevelTestSessionStatus.IN_PROGRESS);
        when(evaluationRepository.findByResponseId(41L))
                .thenReturn(Optional.of(evaluation));
        when(evaluation.isEvaluable()).thenReturn(true);
        when(evaluation.getScore()).thenReturn(88);
        when(evaluation.getReasonCode()).thenReturn(null);

        LevelTestProgressCommandService.ProgressResult result =
                service.replay(response);

        assertThat(result.evaluable()).isTrue();
        assertThat(result.score()).isEqualTo(88);
        assertThat(result.completed()).isFalse();
        verifyNoInteractions(aiClient, progressCommandService, stateService);
    }

    @Test
    void replayWhileOriginalEvaluationIsStillRunningDoesNotStartSecondEvaluation() {
        when(item.getStatus()).thenReturn(LevelTestItemStatus.EVALUATING);
        when(evaluationRepository.findByResponseId(41L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replay(response))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("평가가 아직 진행 중");

        verifyNoInteractions(aiClient, progressCommandService, stateService);
    }
    @Test
    void replayOfFailedEvaluationWithoutStoredResultReturnsFailureWithoutCallingAi() {
        when(item.getStatus()).thenReturn(LevelTestItemStatus.EVALUATION_FAILED);
        when(evaluationRepository.findByResponseId(41L))
                .thenReturn(Optional.empty());

        LevelTestProgressCommandService.ProgressResult result =
                service.replay(response);

        assertThat(result.evaluable()).isFalse();
        assertThat(result.score()).isNull();
        assertThat(result.reasonCode()).isEqualTo("LEVEL_TEST_EVALUATION_FAILED");
        verifyNoInteractions(aiClient, progressCommandService, stateService);
    }

}
