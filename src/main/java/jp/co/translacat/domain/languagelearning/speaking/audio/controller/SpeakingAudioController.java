package jp.co.translacat.domain.languagelearning.speaking.audio.controller;

import jp.co.translacat.domain.languagelearning.speaking.audio.model.SpeakingAudioObject;
import jp.co.translacat.domain.languagelearning.speaking.audio.service.SpeakingAudioQueryService;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/language-learning/speaking/sessions/{sessionId}")
@RequiredArgsConstructor
public class SpeakingAudioController {

    private final SpeakingAudioQueryService audioQueryService;

    @GetMapping("/audio/opening")
    public ResponseEntity<byte[]> getOpening(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return response(
                audioQueryService.getOpeningAudio(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId
                )
        );
    }

    @GetMapping("/turns/{turnId}/audio/user")
    public ResponseEntity<byte[]> getUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable Long turnId
    ) {
        return response(
                audioQueryService.getUserAudio(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId,
                        turnId
                )
        );
    }

    @GetMapping("/turns/{turnId}/audio")
    public ResponseEntity<byte[]> getAssistant(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable Long turnId
    ) {
        return response(
                audioQueryService.getAssistantAudio(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId,
                        turnId
                )
        );
    }

    private ResponseEntity<byte[]> response(SpeakingAudioObject audio) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(audio.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType)
                .body(audio.bytes());
    }
}
