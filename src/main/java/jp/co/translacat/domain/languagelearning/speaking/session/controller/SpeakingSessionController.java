package jp.co.translacat.domain.languagelearning.speaking.session.controller;

import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCompleteRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingSessionDetailResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingSessionResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.session.facade.SpeakingSessionFacade;
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
@RequestMapping("/api/v1/language-learning/speaking/sessions")
@RequiredArgsConstructor
public class SpeakingSessionController {

    private final SpeakingSessionFacade sessionFacade;

    @PostMapping
    public ResponseDto<SpeakingSessionResponseDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SpeakingSessionCreateRequestDto request
    ) {
        return ResponseUtil.ok(
                sessionFacade.create(
                        SecurityUtil.getLoginUserId(principal),
                        request
                )
        );
    }

    @PostMapping("/{sessionId}/complete")
    public ResponseDto<SpeakingSessionResponseDto> complete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @RequestBody(required = false) SpeakingSessionCompleteRequestDto request
    ) {
        return ResponseUtil.ok(
                sessionFacade.complete(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId,
                        request
                )
        );
    }

    @GetMapping("/{sessionId}")
    public ResponseDto<SpeakingSessionDetailResponseDto> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(
                sessionFacade.get(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId
                )
        );
    }

    @GetMapping("/active")
    public ResponseDto<SpeakingSessionDetailResponseDto> getActive(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseUtil.ok(
                sessionFacade.getActive(
                        SecurityUtil.getLoginUserId(principal)
                )
        );
    }
}
