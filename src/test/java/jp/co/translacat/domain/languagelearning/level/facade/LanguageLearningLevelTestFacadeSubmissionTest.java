package jp.co.translacat.domain.languagelearning.level.facade;

import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAudioAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.pool.service.LevelTestQuestionPrefetchPublisher;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestAnswerCommandService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestAudioService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestEvaluationService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestProgressCommandService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestQueryService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestQuestionService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestResultQueryService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestReviewAudioService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestSessionCommandService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageLearningLevelTestFacadeSubmissionTest {

    @Mock
    private LevelTestQueryService levelTestQueryService;
    @Mock
    private LevelTestSessionCommandService sessionCommandService;
    @Mock
    private LevelTestQuestionService questionService;
    @Mock
    private LevelTestQuestionPrefetchPublisher prefetchPublisher;
    @Mock
    private LevelTestAnswerCommandService answerCommandService;
    @Mock
    private LevelTestEvaluationService evaluationService;
    @Mock
    private LevelTestAudioService audioService;
    @Mock
    private LevelTestResultQueryService resultQueryService;
    @Mock
    private LevelTestReviewAudioService reviewAudioService;
    @Mock
    private LevelTestSession session;
    @Mock
    private LevelTestItem item;
    @Mock
    private LevelTestResponse response;
    @Mock
    private MultipartFile audio;

    private LanguageLearningLevelTestFacade facade;

    @BeforeEach
    void setUp() {
        facade = new LanguageLearningLevelTestFacade(
                levelTestQueryService,
                sessionCommandService,
                questionService,
                prefetchPublisher,
                answerCommandService,
                evaluationService,
                audioService,
                resultQueryService,
                reviewAudioService
        );
    }

    @Test
    void acceptedTextAnswerDoesNotGenerateNextQuestionInsidePost() {
        stubTextResultSession();
        LevelAnswerRequestDto request = new LevelAnswerRequestDto(
                "A",
                List.of(),
                null,
                "answer-key"
        );
        when(answerCommandService.prepareText(7L, 1L, 10L, request))
                .thenReturn(new LevelTestAnswerCommandService.PreparedResponse(
                        item,
                        response,
                        false
                ));
        when(evaluationService.evaluate(response, null, null, null))
                .thenReturn(new LevelTestProgressCommandService.ProgressResult(
                        true,
                        88,
                        null,
                        false
                ));

        LevelAnswerResultResponseDto result = facade.submit(
                7L,
                1L,
                10L,
                request
        );

        assertThat(result.nextQuestion()).isNull();
        assertThat(result.score()).isEqualTo(88);
        verify(questionService, never()).getOrGenerateCurrent(any());
        verify(prefetchPublisher, never()).publish(any());
    }

    @Test
    void sameKeyReplayUsesStoredEvaluationInsteadOfEvaluatingAgain() {
        stubTextResultSession();
        LevelAnswerRequestDto request = new LevelAnswerRequestDto(
                "A",
                List.of(),
                null,
                "answer-key"
        );
        when(answerCommandService.prepareText(7L, 1L, 10L, request))
                .thenReturn(new LevelTestAnswerCommandService.PreparedResponse(
                        item,
                        response,
                        true
                ));
        when(evaluationService.replay(response))
                .thenReturn(new LevelTestProgressCommandService.ProgressResult(
                        true,
                        88,
                        null,
                        false
                ));

        LevelAnswerResultResponseDto result = facade.submit(
                7L,
                1L,
                10L,
                request
        );

        assertThat(result.nextQuestion()).isNull();
        verify(evaluationService, never()).evaluate(any(), any(), any(), any());
        verify(evaluationService).replay(response);
    }

    private void stubTextResultSession() {
        when(levelTestQueryService.getOwnedSession(7L, 1L))
                .thenReturn(session);
        when(session.currentQuestionNumber()).thenReturn(11);
        when(session.getTotalQuestions()).thenReturn(20);
    }

    @Test
    void acceptedAudioAnswerDoesNotGenerateNextQuestionInsidePost() {
        LocalDateTime retentionUntil = LocalDateTime.now().plusDays(7);
        byte[] bytes = {1, 2, 3};
        when(audioService.prepareAndStore(
                eq(7L),
                eq(1L),
                eq(10L),
                eq(audio),
                eq(5_000),
                eq("audio-key")
        )).thenReturn(new LevelTestAudioService.StoredAudio(
                item,
                response,
                false,
                retentionUntil,
                bytes,
                "answer.webm",
                "audio/webm"
        ));
        when(evaluationService.evaluate(
                response,
                bytes,
                "answer.webm",
                "audio/webm"
        )).thenReturn(new LevelTestProgressCommandService.ProgressResult(
                true,
                82,
                null,
                false
        ));

        LevelAudioAnswerResultResponseDto result = facade.submitAudio(
                7L,
                1L,
                10L,
                audio,
                5_000,
                "audio-key"
        );

        assertThat(result.nextQuestion()).isNull();
        assertThat(result.retentionUntil()).isEqualTo(retentionUntil);
        verify(questionService, never()).getOrGenerateCurrent(any());
        verify(prefetchPublisher, never()).publish(any());
    }
}
