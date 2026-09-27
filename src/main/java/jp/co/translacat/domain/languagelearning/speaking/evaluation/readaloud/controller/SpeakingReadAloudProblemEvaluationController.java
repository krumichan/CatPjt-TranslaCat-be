package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.controller;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.dto.SpeakingReadAloudProblemEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.port.SpeakingGateway;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/language-learning/speaking/sessions/{sessionId}/read-aloud/problems")
@RequiredArgsConstructor
public class SpeakingReadAloudProblemEvaluationController {

    private final SpeakingGateway gateway;

    @PostMapping("/{problemIndex}/evaluate")
    public ResponseDto<SpeakingReadAloudProblemEvaluationResponseDto> evaluate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable int problemIndex
    ) {
        return ResponseUtil.ok(
                gateway.post(
                        SecurityUtil.getLoginUserId(principal),
                        "/sessions/" + sessionId + "/read-aloud/problems/" + problemIndex + "/evaluate",
                        null, SpeakingReadAloudProblemEvaluationResponseDto.class
                )
        );
    }

    @PostMapping("/{problemIndex}/evaluation/retry")
    public ResponseDto<SpeakingReadAloudProblemEvaluationResponseDto> retry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable int problemIndex
    ) {
        return ResponseUtil.ok(gateway.post(SecurityUtil.getLoginUserId(principal),
                "/sessions/" + sessionId + "/read-aloud/problems/" + problemIndex + "/evaluation/retry",
                null, SpeakingReadAloudProblemEvaluationResponseDto.class));
    }

    @GetMapping
    public ResponseDto<List<SpeakingReadAloudProblemEvaluationResponseDto>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(
                gateway.list(
                        SecurityUtil.getLoginUserId(principal),
                        "/sessions/" + sessionId + "/read-aloud/problems",
                        SpeakingReadAloudProblemEvaluationResponseDto.class
                )
        );
    }
}
