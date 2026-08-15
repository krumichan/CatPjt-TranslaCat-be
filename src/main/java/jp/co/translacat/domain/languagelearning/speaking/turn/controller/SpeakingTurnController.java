package jp.co.translacat.domain.languagelearning.speaking.turn.controller;

import jp.co.translacat.domain.languagelearning.speaking.turn.dto.request.SpeakingTurnProcessRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.request.SpeakingTurnUploadGrantRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnUploadGrantResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.turn.facade.SpeakingTurnFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/language-learning/speaking/sessions/{sessionId}")
@RequiredArgsConstructor
public class SpeakingTurnController {

    private final SpeakingTurnFacade turnFacade;

    @PostMapping("/turns/upload-url")
    public ResponseDto<SpeakingTurnUploadGrantResponseDto> createUploadGrant(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @org.springframework.web.bind.annotation.RequestBody SpeakingTurnUploadGrantRequestDto request
    ) {
        return ResponseUtil.ok(
                turnFacade.createUploadGrant(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId,
                        request
                )
        );
    }

    @PostMapping(value = "/turns", consumes = "multipart/form-data")
    public ResponseDto<SpeakingTurnResponseDto> process(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @RequestPart("context") SpeakingTurnProcessRequestDto request,
            @RequestPart("audio") MultipartFile audio
    ) {
        return ResponseUtil.ok(
                turnFacade.process(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId,
                        request,
                        audio
                )
        );
    }

    @GetMapping("/turns/{turnId}")
    public ResponseDto<SpeakingTurnResponseDto> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable Long turnId
    ) {
        return ResponseUtil.ok(
                turnFacade.get(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId,
                        turnId
                )
        );
    }

    @PostMapping("/turns/{turnId}/retry")
    public ResponseDto<SpeakingTurnResponseDto> retry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable Long turnId
    ) {
        return ResponseUtil.ok(
                turnFacade.retry(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId,
                        turnId
                )
        );
    }

    @PostMapping("/turns/{turnId}/exclude")
    public ResponseDto<SpeakingTurnResponseDto> exclude(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable Long turnId
    ) {
        return ResponseUtil.ok(
                turnFacade.exclude(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId,
                        turnId
                )
        );
    }
}
