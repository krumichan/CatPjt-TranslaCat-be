package jp.co.translacat.domain.languagelearning.listening.session.facade;

import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.port.ListeningGateway;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 기존 Controller DTO를 유지하며 세션·답변·평가·오디오 업무를 LL에 전달한다.
 */
@Service
@RequiredArgsConstructor
public class ListeningSessionFacade {
    private final ListeningGateway gateway;

    public ListeningApiContract.SessionView create(Long userId, ListeningApiContract.SessionCreateRequest request) {
        return gateway.post(userId, "/sessions", request, ListeningApiContract.SessionView.class);
    }

    public ListeningApiContract.ActiveSessionView active(Long userId) {
        return gateway.get(userId, "/sessions/active", ListeningApiContract.ActiveSessionView.class);
    }

    public ListeningApiContract.SessionView get(Long userId, Long sessionId) {
        return gateway.get(userId, "/sessions/" + sessionId, ListeningApiContract.SessionView.class);
    }

    public ListeningApiContract.SessionView resume(Long userId, Long sessionId) {
        return gateway.post(userId, "/sessions/" + sessionId + "/resume", null, ListeningApiContract.SessionView.class);
    }

    public ListeningApiContract.ItemView item(Long userId, Long sessionId, Long itemId) {
        return gateway.get(userId, "/sessions/" + sessionId + "/items/" + itemId, ListeningApiContract.ItemView.class);
    }

    public ListeningApiContract.TaskView upsertText(Long userId, Long attemptId, ListeningTaskType taskType,
                                                    ListeningApiContract.ResponseUpsertRequest request) {
        return gateway.post(userId, "/attempts/" + attemptId + "/responses/" + taskType, request,
                ListeningApiContract.TaskView.class);
    }

    public ListeningApiContract.TaskView applyAssistance(Long userId, Long attemptId, ListeningTaskType taskType,
                                                         List<ListeningApiContract.AssistanceUsage> usage) {
        return gateway.post(userId, "/attempts/" + attemptId + "/responses/" + taskType + "/assistance",
                usage == null ? List.of() : usage, ListeningApiContract.TaskView.class);
    }

    public ListeningApiContract.AttemptView useAssistance(Long userId, Long attemptId, ListeningAssistanceType type) {
        return gateway.post(userId, "/attempts/" + attemptId + "/assistance/" + type, null,
                ListeningApiContract.AttemptView.class);
    }

    public ListeningAudioObject userAudio(Long userId, Long responseId) {
        return gateway.audio(userId, "/responses/" + responseId + "/audio");
    }

    public ListeningApiContract.AudioUploadView uploadAudio(Long userId, Long attemptId, MultipartFile audio,
                                                            int durationMs) {
        // 외부 multipart의 바이트·Content-Type·신고 길이를 전달하고 유효성·저장 소유권은 LL이 판정한다.
        try {
            return gateway.post(userId, "/attempts/" + attemptId + "/audio-upload", Map.of(
                    "audioBase64", Base64.getEncoder().encodeToString(audio.getBytes()),
                    "contentType", audio.getContentType() == null ? "application/octet-stream" : audio.getContentType(),
                    "durationMs", durationMs), ListeningApiContract.AudioUploadView.class);
        } catch (IOException failure) {
            throw new BusinessException("Listening Audio를 읽을 수 없습니다.", "LISTENING_AUDIO_INVALID");
        }
    }

    public ListeningApiContract.AttemptView submit(Long userId, Long attemptId,
                                                   ListeningApiContract.SubmitRequest request) {
        return gateway.post(userId, "/attempts/" + attemptId + "/submit", request,
                ListeningApiContract.AttemptView.class);
    }

    public ListeningApiContract.AttemptView retryEvaluation(Long userId, Long attemptId,
                                                            ListeningApiContract.RetryRequest request) {
        return gateway.post(userId, "/attempts/" + attemptId + "/retry-evaluation", request,
                ListeningApiContract.AttemptView.class);
    }

    public ListeningApiContract.BulkRetryView retryFailedEvaluations(Long userId, Long sessionId) {
        return gateway.post(userId, "/sessions/" + sessionId + "/retry-failed-evaluations", null,
                ListeningApiContract.BulkRetryView.class);
    }

    public ListeningApiContract.RevealAnswerView revealAnswer(Long userId, Long attemptId) {
        return gateway.post(userId, "/attempts/" + attemptId + "/answer", null,
                ListeningApiContract.RevealAnswerView.class);
    }

    public ListeningApiContract.AttemptView createPractice(Long userId, Long sessionId, Long itemId,
                                                           ListeningApiContract.PracticeAttemptRequest request) {
        return gateway.post(userId, "/sessions/" + sessionId + "/items/" + itemId + "/practice-attempts", request,
                ListeningApiContract.AttemptView.class);
    }

    public ListeningApiContract.AttemptView skip(Long userId, Long attemptId,
                                                 ListeningApiContract.SkipRequest request) {
        return gateway.post(userId, "/attempts/" + attemptId + "/skip", request,
                ListeningApiContract.AttemptView.class);
    }

    public ListeningApiContract.EvaluationReportView report(Long userId, Long responseId,
                                                            ListeningApiContract.EvaluationReportRequest request) {
        return gateway.post(userId, "/responses/" + responseId + "/reports", request,
                ListeningApiContract.EvaluationReportView.class);
    }

    public void recordPlayback(Long userId, Long sessionId, Long itemId, ListeningApiContract.PlaybackRequest request) {
        gateway.post(userId, "/sessions/" + sessionId + "/items/" + itemId + "/playbacks", request, Void.class);
    }

    public ListeningApiContract.SessionResultView complete(Long userId, Long sessionId,
                                                           ListeningApiContract.SessionCompleteRequest request) {
        return gateway.post(userId, "/sessions/" + sessionId + "/complete", request,
                ListeningApiContract.SessionResultView.class);
    }

    public ListeningApiContract.SessionResultView result(Long userId, Long sessionId) {
        return gateway.get(userId, "/sessions/" + sessionId + "/result", ListeningApiContract.SessionResultView.class);
    }
}
