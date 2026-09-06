package jp.co.translacat.domain.languagelearning.listening.controller;

import io.swagger.v3.oas.annotations.Operation;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.session.facade.ListeningSessionFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/language-learning/listening")
@RequiredArgsConstructor
public class ListeningSessionController {

    private final ListeningSessionFacade facade;

    @Operation(summary = "Task 조합을 고정한 Listening Session 생성")
    @PostMapping("/sessions")
    public ResponseDto<ListeningApiContract.SessionView> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ListeningApiContract.SessionCreateRequest request
    ) {
        return ResponseUtil.ok(facade.create(userId(principal), request));
    }

    @Operation(summary = "현재 진행 중인 Listening Session 조회")
    @GetMapping("/sessions/active")
    public ResponseDto<ListeningApiContract.ActiveSessionView> active(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseUtil.ok(facade.active(userId(principal)));
    }

    @Operation(summary = "Listening Session 조회")
    @GetMapping("/sessions/{sessionId}")
    public ResponseDto<ListeningApiContract.SessionView> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(facade.get(userId(principal), sessionId));
    }

    @Operation(summary = "2시간 이내 Listening Session 재개")
    @PostMapping("/sessions/{sessionId}/resume")
    public ResponseDto<ListeningApiContract.SessionView> resume(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(facade.resume(userId(principal), sessionId));
    }

    @Operation(summary = "제출 전 정답을 숨긴 Listening 문항 조회")
    @GetMapping("/sessions/{sessionId}/items/{itemId}")
    public ResponseDto<ListeningApiContract.ItemView> item(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable Long itemId
    ) {
        return ResponseUtil.ok(facade.item(
                userId(principal), sessionId, itemId));
    }

    @Operation(summary = "Dictation/Interpretation 텍스트 응답 저장")
    @PostMapping("/attempts/{attemptId}/responses/{taskType}")
    public ResponseDto<ListeningApiContract.TaskView> response(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long attemptId,
            @PathVariable ListeningTaskType taskType,
            @RequestBody ListeningApiContract.ResponseUpsertRequest request
    ) {
        return ResponseUtil.ok(facade.upsertText(
                userId(principal), attemptId, taskType, request));
    }

    @Operation(summary = "Task별 도움 사용 기록")
    @PostMapping("/attempts/{attemptId}/responses/{taskType}/assistance")
    public ResponseDto<ListeningApiContract.TaskView> assistance(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long attemptId,
            @PathVariable ListeningTaskType taskType,
            @RequestBody List<ListeningApiContract.AssistanceUsage> usage
    ) {
        return ResponseUtil.ok(facade.applyAssistance(
                userId(principal), attemptId, taskType, usage));
    }

    @Operation(summary = "문항 전체 도움 사용 1회 기록")
    @PostMapping("/attempts/{attemptId}/assistance/{assistanceType}")
    public ResponseDto<ListeningApiContract.AttemptView> attemptAssistance(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long attemptId,
            @PathVariable ListeningAssistanceType assistanceType
    ) {
        return ResponseUtil.ok(facade.useAssistance(
                userId(principal), attemptId, assistanceType));
    }

    @Operation(summary = "Repeat Task Audio 업로드 또는 재녹음")
    @PostMapping(value = "/attempts/{attemptId}/audio-upload",
            consumes = "multipart/form-data")
    public ResponseDto<ListeningApiContract.AudioUploadView> audioUpload(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long attemptId,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam int durationMs
    ) {
        return ResponseUtil.ok(facade.uploadAudio(
                userId(principal), attemptId, audio, durationMs));
    }

    @Operation(summary = "선택 Task를 독립 Outbox로 비동기 평가 요청")
    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseDto<ListeningApiContract.AttemptView> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long attemptId,
            @RequestBody(required = false) ListeningApiContract.SubmitRequest request
    ) {
        return ResponseUtil.ok(facade.submit(
                userId(principal), attemptId, request));
    }

    @Operation(summary = "실패 Task 하나만 수동 평가 재시도")
    @PostMapping("/attempts/{attemptId}/retry-evaluation")
    public ResponseDto<ListeningApiContract.AttemptView> retry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long attemptId,
            @RequestBody ListeningApiContract.RetryRequest request
    ) {
        return ResponseUtil.ok(facade.retryEvaluation(
                userId(principal), attemptId, request));
    }

    @Operation(summary = "Session 내 실패한 Listening 평가 전체 수동 재시도")
    @PostMapping("/sessions/{sessionId}/retry-failed-evaluations")
    public ResponseDto<ListeningApiContract.BulkRetryView> retryFailed(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(facade.retryFailedEvaluations(
                userId(principal), sessionId));
    }

    @Operation(summary = "정답 공개: 문항 전체 GUIDED 및 평가/진척 제외")
    @PostMapping("/attempts/{attemptId}/answer")
    public ResponseDto<ListeningApiContract.RevealAnswerView> answer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long attemptId
    ) {
        return ResponseUtil.ok(facade.revealAnswer(
                userId(principal), attemptId));
    }

    @Operation(summary = "공식 Attempt 이후 1회 연습 Attempt 생성")
    @PostMapping("/sessions/{sessionId}/items/{itemId}/practice-attempts")
    public ResponseDto<ListeningApiContract.AttemptView> practice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @RequestBody(required = false)
            ListeningApiContract.PracticeAttemptRequest request
    ) {
        return ResponseUtil.ok(facade.createPractice(
                userId(principal), sessionId, itemId, request));
    }

    @Operation(summary = "Listening 문항 건너뛰기(일일 완료 미반영)")
    @PostMapping("/attempts/{attemptId}/skip")
    public ResponseDto<ListeningApiContract.AttemptView> skip(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long attemptId,
            @RequestBody(required = false) ListeningApiContract.SkipRequest request
    ) {
        return ResponseUtil.ok(facade.skip(
                userId(principal), attemptId, request));
    }

    @Operation(summary = "보관 기간 내 사용자 Repeat Audio 조회")
    @GetMapping("/responses/{taskResponseId}/audio")
    public ResponseEntity<byte[]> userAudio(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskResponseId
    ) {
        var audio = facade.userAudio(userId(principal), taskResponseId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(audio.contentType()))
                .body(audio.bytes());
    }

    @Operation(summary = "Listening 평가 오류 신고")
    @PostMapping("/responses/{taskResponseId}/reports")
    public ResponseDto<ListeningApiContract.EvaluationReportView> report(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskResponseId,
            @RequestBody ListeningApiContract.EvaluationReportRequest request
    ) {
        return ResponseUtil.ok(facade.report(
                userId(principal), taskResponseId, request));
    }

    @Operation(summary = "Listening 재생 이벤트 기록")
    @PostMapping("/sessions/{sessionId}/items/{itemId}/playbacks")
    public ResponseDto<Void> playback(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @RequestBody ListeningApiContract.PlaybackRequest request
    ) {
        facade.recordPlayback(userId(principal), sessionId, itemId, request);
        return ResponseUtil.ok(null);
    }

    @Operation(summary = "Listening Session 완료")
    @PostMapping("/sessions/{sessionId}/complete")
    public ResponseDto<ListeningApiContract.SessionResultView> complete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @RequestBody(required = false)
            ListeningApiContract.SessionCompleteRequest request
    ) {
        return ResponseUtil.ok(facade.complete(
                userId(principal), sessionId, request));
    }

    @Operation(summary = "Listening Session 결과")
    @GetMapping("/sessions/{sessionId}/result")
    public ResponseDto<ListeningApiContract.SessionResultView> result(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(facade.result(
                userId(principal), sessionId));
    }

    private Long userId(UserPrincipal principal) {
        return SecurityUtil.getLoginUserId(principal);
    }
}
