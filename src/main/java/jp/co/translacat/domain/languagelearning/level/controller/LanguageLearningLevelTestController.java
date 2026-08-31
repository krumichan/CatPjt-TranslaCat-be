package jp.co.translacat.domain.languagelearning.level.controller;

import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.request.LevelTestStartRequestDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelAudioAnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelSessionResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelStatusResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestHistoryDetailResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestResultResponseDto;
import jp.co.translacat.domain.languagelearning.level.facade.LanguageLearningLevelTestFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
                levelTestFacade.getStatus(userId(userPrincipal))
        );
    }

    @PostMapping("/sessions")
    public ResponseDto<LevelSessionResponseDto> start(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody LevelTestStartRequestDto request
    ) {
        return ResponseUtil.ok(
                levelTestFacade.start(userId(userPrincipal), request)
        );
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseDto<LevelSessionResponseDto> getSession(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(
                levelTestFacade.getSession(
                        userId(userPrincipal),
                        sessionId
                )
        );
    }

    @GetMapping("/sessions/{sessionId}/current-item")
    public ResponseDto<LevelQuestionResponseDto> getCurrent(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(
                levelTestFacade.getCurrent(
                        userId(userPrincipal),
                        sessionId
                )
        );
    }

    @GetMapping("/items/{itemId}/reference-audio")
    public ResponseEntity<byte[]> getReferenceAudio(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long itemId
    ) {
        var audio = levelTestFacade.getReferenceAudio(
                userId(userPrincipal),
                itemId
        );
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.contentType()))
                .body(audio.bytes());
    }

    @PostMapping("/sessions/{sessionId}/items/{itemId}/answers")
    public ResponseDto<LevelAnswerResultResponseDto> submitAnswer(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @RequestBody LevelAnswerRequestDto request
    ) {
        return ResponseUtil.ok(
                levelTestFacade.submit(
                        userId(userPrincipal),
                        sessionId,
                        itemId,
                        request
                )
        );
    }

    @PostMapping(
            value = "/sessions/{sessionId}/items/{itemId}/answers/audio",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseDto<LevelAudioAnswerResultResponseDto> submitAudioAnswer(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam Integer durationMs,
            @RequestParam String idempotencyKey
    ) {
        return ResponseUtil.ok(
                levelTestFacade.submitAudio(
                        userId(userPrincipal),
                        sessionId,
                        itemId,
                        audio,
                        durationMs,
                        idempotencyKey
                )
        );
    }

    @PostMapping("/sessions/{sessionId}/items/{itemId}/evaluation/retry")
    public ResponseDto<LevelAnswerResultResponseDto> retryEvaluation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long sessionId,
            @PathVariable Long itemId
    ) {
        return ResponseUtil.ok(
                levelTestFacade.retryEvaluation(
                        userId(userPrincipal),
                        sessionId,
                        itemId
                )
        );
    }

    @GetMapping("/sessions/{sessionId}/result")
    public ResponseDto<LevelTestResultResponseDto> getResult(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(
                levelTestFacade.getResult(
                        userId(userPrincipal),
                        sessionId
                )
        );
    }

    @GetMapping("/history")
    public ResponseDto<List<LevelTestHistoryItemResponseDto>> getHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                levelTestFacade.getHistory(userId(userPrincipal))
        );
    }

    @GetMapping("/history/{sessionId}")
    public ResponseDto<LevelTestHistoryDetailResponseDto> getHistoryDetail(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long sessionId
    ) {
        return ResponseUtil.ok(
                levelTestFacade.getHistoryDetail(
                        userId(userPrincipal),
                        sessionId
                )
        );
    }

    private Long userId(UserPrincipal userPrincipal) {
        return SecurityUtil.getLoginUserId(userPrincipal);
    }
}
