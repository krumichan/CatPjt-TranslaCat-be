package jp.co.translacat.domain.languagelearning.level.controller;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelStatusResponseDto;
import jp.co.translacat.domain.languagelearning.level.facade.LanguageLearningLevelTestFacade;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/language-learning/level-test")
@RequiredArgsConstructor
public class LanguageLearningLevelTestController {

    private final LanguageLearningLevelTestFacade levelTestFacade;

    @GetMapping("/status")
    public ResponseDto<LevelStatusResponseDto> getStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                levelTestFacade.getStatus(
                        SecurityUtil.getLoginUserId(userPrincipal)
                )
        );
    }

    @PostMapping("/start")
    public ResponseDto<LevelQuestionResponseDto> start(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "INITIAL")
            LevelTestSessionType type
    ) {
        return ResponseUtil.ok(
                levelTestFacade.start(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        type
                )
        );
    }

    @GetMapping("/sessions/{sessionId}/current")
    public ResponseDto<LevelQuestionResponseDto> getCurrent(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(
                levelTestFacade.getCurrent(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        sessionId
                )
        );
    }

    @PostMapping("/sessions/{sessionId}/answers")
    public ResponseDto<LevelAnswerResultResponseDto> submitAnswer(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long sessionId,
            @RequestBody LevelAnswerRequestDto request
    ) {
        return ResponseUtil.ok(
                levelTestFacade.submit(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        sessionId,
                        request
                )
        );
    }
}
