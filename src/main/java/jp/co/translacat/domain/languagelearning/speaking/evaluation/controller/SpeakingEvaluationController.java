package jp.co.translacat.domain.languagelearning.speaking.evaluation.controller;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.dto.response.SpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.facade.SpeakingEvaluationFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/language-learning/speaking/sessions/{sessionId}/evaluation")
@RequiredArgsConstructor
public class SpeakingEvaluationController {

    private final SpeakingEvaluationFacade evaluationFacade;

    @GetMapping
    public ResponseDto<SpeakingEvaluationResponseDto> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(
                evaluationFacade.get(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId
                )
        );
    }

    @PostMapping("/retry")
    public ResponseDto<Void> retry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        evaluationFacade.retry(
                SecurityUtil.getLoginUserId(principal),
                sessionId
        );
        return ResponseUtil.ok(null);
    }
}
