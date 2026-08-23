package jp.co.translacat.domain.languagelearning.listening.session.facade;

import jp.co.translacat.domain.languagelearning.listening.attempt.service.ListeningAttemptCommandService;
import jp.co.translacat.domain.languagelearning.listening.attempt.service.ListeningAttemptQueryService;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.report.service.ListeningEvaluationReportCommandService;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionCommandService;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningSessionFacade {

    private final ListeningSessionCommandService sessionCommandService;
    private final ListeningSessionQueryService sessionQueryService;
    private final ListeningAttemptCommandService attemptCommandService;
    private final ListeningAttemptQueryService attemptQueryService;
    private final ListeningEvaluationReportCommandService reportCommandService;

    public ListeningApiContract.SessionView create(
            Long userId,
            ListeningApiContract.SessionCreateRequest request
    ) {
        Long sessionId = sessionCommandService.create(userId, request);

        return sessionQueryService.view(userId, sessionId);
    }

    public ListeningApiContract.ActiveSessionView active(Long userId) {
        Long sessionId = sessionCommandService.activeSessionId(userId);
        return sessionId == null
                ? new ListeningApiContract.ActiveSessionView(false, null)
                : new ListeningApiContract.ActiveSessionView(
                        true,
                        sessionQueryService.view(userId, sessionId)
                );
    }

    public ListeningApiContract.SessionView get(Long userId, Long sessionId) {
        sessionCommandService.expireIfNeeded(userId, sessionId);

        return sessionQueryService.view(userId, sessionId);
    }

    public ListeningApiContract.SessionView resume(
            Long userId,
            Long sessionId
    ) {
        var result = sessionCommandService.resume(userId, sessionId);

        if (result.expired()) {
            throw sessionExpired("Listening Session 재개 시간이 만료되었습니다.");
        }

        return sessionQueryService.view(userId, result.sessionId());
    }

    public ListeningApiContract.ItemView item(
            Long userId,
            Long sessionId,
            Long itemId
    ) {
        return attemptQueryService.item(userId, sessionId, itemId);
    }

    public ListeningApiContract.TaskView upsertText(
            Long userId,
            Long attemptId,
            ListeningTaskType taskType,
            ListeningApiContract.ResponseUpsertRequest request
    ) {
        return attemptCommandService.upsertText(
                userId,
                attemptId,
                taskType,
                request
        );
    }

    public ListeningApiContract.TaskView applyAssistance(
            Long userId,
            Long attemptId,
            ListeningTaskType taskType,
            List<ListeningApiContract.AssistanceUsage> usage
    ) {
        return attemptCommandService.applyAssistance(
                userId,
                attemptId,
                taskType,
                usage
        );
    }

    public ListeningAudioObject userAudio(
            Long userId,
            Long taskResponseId
    ) {
        return attemptQueryService.userAudio(userId, taskResponseId);
    }

    public ListeningApiContract.AudioUploadView uploadAudio(
            Long userId,
            Long attemptId,
            MultipartFile audio,
            int durationMs
    ) {
        return attemptCommandService.uploadAudio(
                userId,
                attemptId,
                audio,
                durationMs
        );
    }

    public ListeningApiContract.AttemptView submit(
            Long userId,
            Long attemptId,
            ListeningApiContract.SubmitRequest request
    ) {
        return attemptCommandService.submit(userId, attemptId, request);
    }

    public ListeningApiContract.AttemptView retryEvaluation(
            Long userId,
            Long attemptId,
            ListeningApiContract.RetryRequest request
    ) {
        return attemptCommandService.retryEvaluation(
                userId,
                attemptId,
                request
        );
    }

    public ListeningApiContract.RevealAnswerView revealAnswer(
            Long userId,
            Long attemptId
    ) {
        return attemptCommandService.revealAnswer(userId, attemptId);
    }

    public ListeningApiContract.AttemptView createPractice(
            Long userId,
            Long sessionId,
            Long itemId,
            ListeningApiContract.PracticeAttemptRequest request
    ) {
        return attemptCommandService.createPractice(
                userId,
                sessionId,
                itemId,
                request
        );
    }

    public ListeningApiContract.AttemptView skip(
            Long userId,
            Long attemptId,
            ListeningApiContract.SkipRequest request
    ) {
        return attemptCommandService.skip(userId, attemptId, request);
    }

    public ListeningApiContract.EvaluationReportView report(
            Long userId,
            Long taskResponseId,
            ListeningApiContract.EvaluationReportRequest request
    ) {
        return reportCommandService.report(userId, taskResponseId, request);
    }

    public ListeningApiContract.SessionResultView complete(
            Long userId,
            Long sessionId,
            ListeningApiContract.SessionCompleteRequest request
    ) {
        var result = sessionCommandService.complete(userId, sessionId);

        if (result.expired()) {
            throw sessionExpired("Listening Session이 만료되었습니다.");
        }

        return sessionQueryService.result(userId, result.sessionId());
    }

    public ListeningApiContract.SessionResultView result(
            Long userId,
            Long sessionId
    ) {
        return sessionQueryService.result(userId, sessionId);
    }

    private BusinessException sessionExpired(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.LISTENING_SESSION_EXPIRED
        );
    }
}
