package jp.co.translacat.domain.languagelearning.speaking.assistance.controller;

import jp.co.translacat.domain.languagelearning.speaking.assistance.dto.request.SpeakingAssistanceRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.assistance.dto.response.SpeakingAssistanceResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.assistance.facade.SpeakingAssistanceFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/language-learning/speaking/sessions/{sessionId}")
@RequiredArgsConstructor
public class SpeakingAssistanceController {

    private final SpeakingAssistanceFacade assistanceFacade;

    @PostMapping("/assistance")
    public ResponseDto<SpeakingAssistanceResponseDto> getAssistance(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @RequestBody SpeakingAssistanceRequestDto request
    ) {
        return ResponseUtil.ok(
                assistanceFacade.get(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId,
                        request
                )
        );
    }
}
