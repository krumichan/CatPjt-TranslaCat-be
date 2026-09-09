package jp.co.translacat.domain.languagelearning.listening.controller;

import io.swagger.v3.oas.annotations.Operation;

import jp.co.translacat.domain.languagelearning.listening.daily.facade.ListeningDailySetFacade;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/language-learning/listening")
@RequiredArgsConstructor
public class ListeningDailySetController {

    private final ListeningDailySetFacade facade;

    @Operation(summary = "오늘의 Listening 유형별 상태 조회 (생성하지 않음)")
    @GetMapping("/today/status")
    public ResponseDto<java.util.List<ListeningApiContract.DailyModeStatusView>> todayStatus(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseUtil.ok(facade.todayStatuses(
                SecurityUtil.getLoginUserId(principal)
        ));
    }

    @Operation(summary = "오늘의 Listening Daily Set 조회 또는 비동기 생성")
    @GetMapping("/today")
    public ResponseDto<ListeningApiContract.DailySetView> today(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = SecurityUtil.getLoginUserId(principal);
        return ResponseUtil.ok(facade.getOrCreate(userId, null));
    }

    @Operation(summary = "Listening Daily Set 멱등 생성")
    @PostMapping("/daily-sets")
    public ResponseDto<ListeningApiContract.DailySetView> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false)
            ListeningApiContract.DailySetCreateRequest request
    ) {
        Long userId = SecurityUtil.getLoginUserId(principal);
        return ResponseUtil.ok(facade.getOrCreate(userId, request));
    }

    @Operation(summary = "실패한 Listening Daily Set 생성 수동 재시도")
    @PostMapping("/daily-sets/{dailySetId}/retry-generation")
    public ResponseDto<ListeningApiContract.DailySetView> retryGeneration(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long dailySetId
    ) {
        return ResponseUtil.ok(facade.retryGeneration(
                SecurityUtil.getLoginUserId(principal),
                dailySetId
        ));
    }

    @Operation(summary = "Listening Daily Set 생성 진행 조회 (생성하지 않음)")
    @GetMapping("/daily-sets/{dailySetId}")
    public ResponseDto<ListeningApiContract.DailySetView> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long dailySetId
    ) {
        return ResponseUtil.ok(facade.get(SecurityUtil.getLoginUserId(principal), dailySetId));
    }

    @Operation(summary = "실패한 기준 TTS 수동 재시도")
    @PostMapping("/items/{itemId}/retry-tts")
    public ResponseDto<ListeningApiContract.DailySetView> retryTts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long itemId
    ) {
        return ResponseUtil.ok(facade.retryTts(
                SecurityUtil.getLoginUserId(principal),
                itemId
        ));
    }

    @Operation(summary = "보관 기간 내 비공개 기준 Audio 조회")
    @GetMapping("/items/{itemId}/audio")
    public ResponseEntity<byte[]> audio(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long itemId
    ) {
        var audio = facade.referenceAudio(
                SecurityUtil.getLoginUserId(principal),
                itemId
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(audio.contentType()))
                .body(audio.bytes());
    }
}
