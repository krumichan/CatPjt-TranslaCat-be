package jp.co.translacat.domain.languagelearning.level.facade;

import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.request.LevelTestStartRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAudioAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelSessionResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelStatusResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestHistoryDetailResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestAnswerCommandService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestAudioService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestEvaluationService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestProgressCommandService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestQueryService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestQuestionService;
import jp.co.translacat.domain.languagelearning.level.pool.service.LevelTestQuestionPrefetchPublisher;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestResultQueryService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestReviewAudioService;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestSessionCommandService;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LanguageLearningLevelTestFacade {

    private final LevelTestQueryService levelTestQueryService;
    private final LevelTestSessionCommandService sessionCommandService;
    private final LevelTestQuestionService questionService;
    private final LevelTestQuestionPrefetchPublisher prefetchPublisher;
    private final LevelTestAnswerCommandService answerCommandService;
    private final LevelTestEvaluationService evaluationService;
    private final LevelTestAudioService audioService;
    private final LevelTestResultQueryService resultQueryService;
    private final LevelTestReviewAudioService reviewAudioService;

    public LevelStatusResponseDto getStatus(Long userId) {
        return levelTestQueryService.getStatus(userId);
    }

    public LevelSessionResponseDto start(
            Long userId,
            LevelTestStartRequestDto request
    ) {
        LevelTestSession session = sessionCommandService.start(
                userId,
                request == null ? null : request.type(),
                request == null ? null : request.idempotencyKey()
        );
        return toSessionResponse(session);
    }

    public LevelSessionResponseDto getSession(
            Long userId,
            Long sessionId
    ) {
        return toSessionResponse(
                levelTestQueryService.getOwnedSession(userId, sessionId)
        );
    }

    public LevelQuestionResponseDto getCurrent(
            Long userId,
            Long sessionId
    ) {
        LevelTestSession session = levelTestQueryService.getOwnedSession(
                userId,
                sessionId
        );
        LevelTestItem item = questionService.getOrGenerateCurrent(session);
        prefetchPublisher.publish(item);
        return questionService.toResponse(item);
    }

    public ListeningAudioObject getReferenceAudio(
            Long userId,
            Long itemId
    ) {
        return questionService.referenceAudio(userId, itemId);
    }

    public ListeningAudioObject getAnswerAudio(
            Long userId,
            Long itemId
    ) {
        return reviewAudioService.loadAnswerAudio(userId, itemId);
    }

    public ListeningAudioObject getModelAnswerAudio(
            Long userId,
            Long itemId
    ) {
        return reviewAudioService.loadModelAnswerAudio(userId, itemId);
    }

    public LevelAnswerResultResponseDto submit(
            Long userId,
            Long sessionId,
            Long itemId,
            LevelAnswerRequestDto request
    ) {
        LevelTestAnswerCommandService.PreparedResponse prepared =
                answerCommandService.prepareText(
                        userId,
                        sessionId,
                        itemId,
                        request
                );
        LevelTestProgressCommandService.ProgressResult progress =
                prepared.idempotentReplay()
                        ? evaluationService.replay(prepared.response())
                        : evaluationService.evaluate(
                                prepared.response(),
                                null,
                                null,
                                null
                        );
        return toAnswerResult(
                userId,
                sessionId,
                itemId,
                progress
        );
    }

    public LevelAudioAnswerResultResponseDto submitAudio(
            Long userId,
            Long sessionId,
            Long itemId,
            MultipartFile audio,
            Integer durationMs,
            String idempotencyKey
    ) {
        LevelTestAudioService.StoredAudio stored = audioService.prepareAndStore(
                userId,
                sessionId,
                itemId,
                audio,
                durationMs,
                idempotencyKey
        );
        LevelTestProgressCommandService.ProgressResult progress =
                stored.idempotentReplay()
                        ? evaluationService.replay(stored.response())
                        : evaluationService.evaluate(
                                stored.response(),
                                stored.bytes(),
                                stored.fileName(),
                                stored.contentType()
                        );

        return new LevelAudioAnswerResultResponseDto(
                sessionId,
                itemId,
                progress.evaluable(),
                progress.score(),
                progress.reasonCode(),
                progress.completed(),
                null,
                stored.retentionUntil()
        );
    }

    public LevelAnswerResultResponseDto retryEvaluation(
            Long userId,
            Long sessionId,
            Long itemId
    ) {
        LevelTestProgressCommandService.ProgressResult progress =
                evaluationService.retry(userId, sessionId, itemId);
        return toAnswerResult(
                userId,
                sessionId,
                itemId,
                progress
        );
    }

    public LevelTestResultResponseDto getResult(
            Long userId,
            Long sessionId
    ) {
        return resultQueryService.getResult(userId, sessionId);
    }

    public List<LevelTestHistoryItemResponseDto> getHistory(Long userId) {
        return resultQueryService.getHistory(userId);
    }

    public LevelTestHistoryDetailResponseDto getHistoryDetail(
            Long userId,
            Long sessionId
    ) {
        return resultQueryService.getHistoryDetail(userId, sessionId);
    }

    private LevelAnswerResultResponseDto toAnswerResult(
            Long userId,
            Long sessionId,
            Long itemId,
            LevelTestProgressCommandService.ProgressResult progress
    ) {
        LevelTestSession session = levelTestQueryService.getOwnedSession(
                userId,
                sessionId
        );
        return new LevelAnswerResultResponseDto(
                sessionId,
                itemId,
                Math.min(
                        session.currentQuestionNumber(),
                        session.getTotalQuestions()
                ),
                progress.evaluable(),
                progress.score(),
                progress.reasonCode(),
                progress.completed(),
                null
        );
    }

    private LevelSessionResponseDto toSessionResponse(LevelTestSession session) {
        return new LevelSessionResponseDto(
                session.getId(),
                session.getSessionType(),
                session.getStatus(),
                session.getTotalQuestions(),
                session.currentQuestionNumber(),
                session.currentComplexityBand(),
                session.getBaseLevelScore(),
                session.getProficiencyBand(),
                session.getStartedAt(),
                session.getCompletedAt()
        );
    }
}
