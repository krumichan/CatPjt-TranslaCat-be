package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.controller;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.dto.SpeakingReadAloudProblemEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.service.SpeakingReadAloudProblemEvaluationService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/language-learning/speaking/sessions/{sessionId}/read-aloud/problems")
@RequiredArgsConstructor
public class SpeakingReadAloudProblemEvaluationController {

    private final SpeakingReadAloudProblemEvaluationService service;

    @PostMapping("/{problemIndex}/evaluate")
    public ResponseDto<SpeakingReadAloudProblemEvaluationResponseDto> evaluate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @PathVariable int problemIndex
    ) {
        return ResponseUtil.ok(
                service.submit(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId,
                        problemIndex
                )
        );
    }

    @GetMapping
    public ResponseDto<List<SpeakingReadAloudProblemEvaluationResponseDto>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(
                service.list(
                        SecurityUtil.getLoginUserId(principal),
                        sessionId
                )
        );
    }
}
