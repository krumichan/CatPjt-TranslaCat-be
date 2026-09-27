package jp.co.translacat.domain.languagelearning.speaking.report.controller;

import jp.co.translacat.domain.languagelearning.speaking.port.SpeakingGateway;
import jp.co.translacat.domain.languagelearning.speaking.report.dto.request.SttErrorReportCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.report.dto.response.SttErrorReportResponseDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/language-learning/speaking")
@RequiredArgsConstructor
public class SttErrorReportController {

    private final SpeakingGateway gateway;

    @PostMapping("/sessions/{sessionId}/turns/{turnId}/stt-reports")
    public ResponseDto<SttErrorReportResponseDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable Long turnId,
            @RequestBody SttErrorReportCreateRequestDto request
    ) {
        return ResponseUtil.ok(gateway.post(SecurityUtil.getLoginUserId(principal),
                "/sessions/" + sessionId + "/turns/" + turnId + "/stt-reports", request,
                SttErrorReportResponseDto.class));
    }

    @PostMapping("/stt-reports/{reportId}/support")
    public ResponseDto<SttErrorReportResponseDto> requestSupport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long reportId
    ) {
        return ResponseUtil.ok(gateway.post(SecurityUtil.getLoginUserId(principal),
                "/stt-reports/" + reportId + "/support", null, SttErrorReportResponseDto.class));
    }

    @GetMapping("/stt-reports/{reportId}")
    public ResponseDto<SttErrorReportResponseDto> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long reportId
    ) {
        return ResponseUtil.ok(gateway.get(SecurityUtil.getLoginUserId(principal),
                "/stt-reports/" + reportId, SttErrorReportResponseDto.class));
    }
}
