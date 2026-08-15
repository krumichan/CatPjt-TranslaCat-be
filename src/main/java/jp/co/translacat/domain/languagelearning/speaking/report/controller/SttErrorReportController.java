package jp.co.translacat.domain.languagelearning.speaking.report.controller;

import jp.co.translacat.domain.languagelearning.speaking.report.dto.request.SttErrorReportCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.report.dto.response.SttErrorReportResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.report.service.SttErrorReportCommandService;
import jp.co.translacat.domain.languagelearning.speaking.report.service.SttErrorReportQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/language-learning/speaking")
@RequiredArgsConstructor
public class SttErrorReportController {

    private final SttErrorReportCommandService commandService;
    private final SttErrorReportQueryService queryService;

    @PostMapping("/sessions/{sessionId}/turns/{turnId}/stt-reports")
    public ResponseDto<SttErrorReportResponseDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable Long turnId,
            @RequestBody SttErrorReportCreateRequestDto request
    ) {
        var report = commandService.create(
                SecurityUtil.getLoginUserId(principal),
                sessionId,
                turnId,
                request
        );
        return ResponseUtil.ok(queryService.toResponse(report));
    }

    @GetMapping("/stt-reports/{reportId}")
    public ResponseDto<SttErrorReportResponseDto> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long reportId
    ) {
        return ResponseUtil.ok(
                queryService.get(
                        SecurityUtil.getLoginUserId(principal),
                        reportId
                )
        );
    }
}
