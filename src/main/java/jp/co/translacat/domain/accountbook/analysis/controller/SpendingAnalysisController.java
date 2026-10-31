package jp.co.translacat.domain.accountbook.analysis.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.*;
import jp.co.translacat.domain.accountbook.analysis.service.SpendingAnalysisService;
import jp.co.translacat.domain.accountbook.analysis.service.SpendingAnalysisSnapshotService;
import jp.co.translacat.global.dto.ErrorDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account-books/{accountBookId}/spending-analysis")
public class SpendingAnalysisController {
    private final SpendingAnalysisService service;
    private final SpendingAnalysisSnapshotService snapshots;

    @PostMapping
    public ResponseDto<Response> analyze(@PathVariable Long accountBookId,
                                         @AuthenticationPrincipal UserPrincipal user,
                                         @RequestBody @Valid Request request) {
        return ResponseUtil.ok(service.analyze(accountBookId, user.getId(), request));
    }

    @GetMapping("/snapshot")
    public ResponseDto<SnapshotInfo> snapshot(@PathVariable Long accountBookId,
                                             @AuthenticationPrincipal UserPrincipal user,
                                             @RequestParam PeriodType periodType, @RequestParam Integer year,
                                             @RequestParam(required = false) Integer month) {
        return ResponseUtil.ok(snapshots.current(accountBookId, user.getId(), periodType, year, month));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseDto<ErrorDto>> analysisError(ResponseStatusException error) {
        String code = error.getReason();
        return ResponseEntity.status(error.getStatusCode()).body(ResponseDto.<ErrorDto>builder()
                .resultCode(error.getStatusCode().value()).message(code == null ? "SPENDING_ANALYSIS_FAILED" : code)
                .body(ErrorDto.builder().errorCode(code).build()).createDate(LocalDateTime.now()).build());
    }
}
