package jp.co.translacat.domain.accountbook.analysis.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.*;
import jp.co.translacat.domain.accountbook.analysis.service.SpendingAnalysisJobService;
import jp.co.translacat.domain.accountbook.analysis.service.SpendingAnalysisJobService.View;
import jp.co.translacat.domain.accountbook.analysis.service.SpendingAnalysisSnapshotService;
import jp.co.translacat.domain.accountbook.analysis.service.SpendingAnalysisHistoryService;
import jp.co.translacat.global.dto.ErrorDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account-books/{accountBookId}/spending-analysis")
public class SpendingAnalysisController {
    private final SpendingAnalysisJobService jobs;
    private final SpendingAnalysisSnapshotService snapshots;
    private final SpendingAnalysisHistoryService history;

    @PostMapping
    public ResponseDto<Response> analyze(@PathVariable Long accountBookId,
                                         @AuthenticationPrincipal UserPrincipal user,
                                         @RequestBody @Valid Request request) {
        return ResponseUtil.ok(jobs.runLegacy(accountBookId, user.getId(), request));
    }

    @PostMapping("/jobs")
    public ResponseEntity<ResponseDto<View>> enqueue(@PathVariable Long accountBookId,
                                                     @AuthenticationPrincipal UserPrincipal user,
                                                     @RequestBody @Valid Request request) {
        return ResponseEntity.accepted().body(ResponseUtil.ok(HttpStatus.ACCEPTED, jobs.enqueue(accountBookId, user.getId(), request)));
    }

    @GetMapping("/jobs/current")
    public ResponseDto<View> current(@PathVariable Long accountBookId, @AuthenticationPrincipal UserPrincipal user) {
        return ResponseUtil.ok(jobs.current(accountBookId, user.getId()));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseDto<View> job(@PathVariable Long accountBookId, @PathVariable long jobId,
                                 @AuthenticationPrincipal UserPrincipal user) {
        return ResponseUtil.ok(jobs.get(accountBookId, user.getId(), jobId));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseDto<View> cancel(@PathVariable Long accountBookId, @PathVariable long jobId,
                                    @AuthenticationPrincipal UserPrincipal user) {
        return ResponseUtil.ok(jobs.cancel(accountBookId, user.getId(), jobId));
    }

    @GetMapping("/snapshot")
    public ResponseDto<SnapshotInfo> snapshot(@PathVariable Long accountBookId,
                                             @AuthenticationPrincipal UserPrincipal user,
                                             @RequestParam PeriodType periodType, @RequestParam Integer year,
                                             @RequestParam(required = false) Integer month,
                                             @RequestParam(required = false) Integer quarter,
                                             @RequestParam(required = false) Integer half,
                                             @RequestParam(required = false) java.time.LocalDate startDate,
                                             @RequestParam(required = false) java.time.LocalDate endDate) {
        var request = new Request(periodType, year, month, "ko", null, quarter, half, startDate, endDate);
        if (!request.isValidPeriod()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ANALYSIS_PERIOD");
        return ResponseUtil.ok(snapshots.current(accountBookId, user.getId(), request));
    }

    @GetMapping("/history")
    public ResponseDto<HistoryPage> history(@PathVariable Long accountBookId, @AuthenticationPrincipal UserPrincipal user,
                                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseUtil.ok(history.list(accountBookId, user.getId(), page, size));
    }

    @GetMapping("/history/{analysisId}")
    public ResponseDto<Response> saved(@PathVariable Long accountBookId, @AuthenticationPrincipal UserPrincipal user,
                                      @PathVariable String analysisId) {
        return ResponseUtil.ok(history.find(accountBookId, user.getId(), analysisId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseDto<ErrorDto>> analysisError(ResponseStatusException error) {
        String code = error.getReason();
        return ResponseEntity.status(error.getStatusCode()).body(ResponseDto.<ErrorDto>builder()
                .resultCode(error.getStatusCode().value()).message(code == null ? "SPENDING_ANALYSIS_FAILED" : code)
                .body(ErrorDto.builder().errorCode(code).build()).createDate(LocalDateTime.now()).build());
    }
}
