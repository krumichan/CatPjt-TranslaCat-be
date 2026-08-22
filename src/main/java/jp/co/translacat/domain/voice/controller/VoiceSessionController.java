package jp.co.translacat.domain.voice.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.voice.dto.request.VoiceSessionCreateRequestDto;
import jp.co.translacat.domain.voice.dto.request.VoiceSessionUpdateRequestDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSegmentListResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSessionListResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSessionResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceTranslationRetryResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceWebSocketTicketResponseDto;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.facade.VoiceSessionFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/voice/sessions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class VoiceSessionController {

    private final VoiceSessionFacade voiceSessionFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Voice 세션 생성", description = "Voice Translation V2 세션을 생성한다.")
    public ResponseDto<VoiceSessionResponseDto> create(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody VoiceSessionCreateRequestDto request
    ) {
        return ResponseUtil.created(
                voiceSessionFacade.create(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        request
                )
        );
    }

    @GetMapping
    @Operation(summary = "Voice 이력 조회", description = "저장된 Voice Translation V2 세션 이력을 조회한다.")
    public ResponseDto<VoiceSessionListResponseDto> listHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime cursor,
            @RequestParam(defaultValue = "50") Integer size
    ) {
        return ResponseUtil.ok(
                voiceSessionFacade.listHistory(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        cursor,
                        size
                )
        );
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Voice 세션 조회", description = "Voice Translation V2 세션 상세를 조회한다.")
    public ResponseDto<VoiceSessionResponseDto> get(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String sessionId
    ) {
        return ResponseUtil.ok(
                voiceSessionFacade.get(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        sessionId
                )
        );
    }

    @GetMapping("/{sessionId}/segments")
    @Operation(summary = "Voice Segment 조회", description = "Voice Translation V2 Final Segment 이력을 조회한다.")
    public ResponseDto<VoiceSegmentListResponseDto> listSegments(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String sessionId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") Integer size
    ) {
        return ResponseUtil.ok(
                voiceSessionFacade.listSegments(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        sessionId,
                        cursor,
                        size
                )
        );
    }

    @PatchMapping("/{sessionId}")
    @Operation(summary = "Voice 세션 수정", description = "Voice Translation V2 세션 제목을 수정한다.")
    public ResponseDto<VoiceSessionResponseDto> update(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String sessionId,
            @RequestBody VoiceSessionUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                voiceSessionFacade.update(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        sessionId,
                        request
                )
        );
    }

    @PostMapping("/{sessionId}/complete")
    @Operation(summary = "Voice 세션 완료", description = "열린 Voice Stream을 Flush한 후 세션을 완료한다.")
    public ResponseDto<VoiceSessionResponseDto> complete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String sessionId
    ) {
        return ResponseUtil.ok(
                voiceSessionFacade.complete(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        sessionId
                )
        );
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Voice 이력 삭제", description = "완료된 Voice 세션과 Segment 이력을 삭제한다.")
    public ResponseDto<Void> delete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String sessionId
    ) {
        voiceSessionFacade.delete(
                SecurityUtil.getLoginUserId(userPrincipal),
                sessionId
        );

        return ResponseUtil.noContent();
    }

    @PostMapping("/{sessionId}/channels/{channel}/ticket")
    @Operation(summary = "Voice WebSocket Ticket 발급", description = "Voice Channel WebSocket 접속용 단기 Ticket을 발급한다.")
    public ResponseDto<VoiceWebSocketTicketResponseDto> issueWebSocketTicket(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String sessionId,
            @PathVariable VoiceChannel channel
    ) {
        return ResponseUtil.ok(
                voiceSessionFacade.issueWebSocketTicket(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        sessionId,
                        channel
                )
        );
    }

    @PostMapping("/{sessionId}/segments/{segmentId}/retry-translation")
    @Operation(summary = "Voice 번역 재시도", description = "STT Final 원문을 기준으로 AI 번역만 재시도한다.")
    public ResponseDto<VoiceTranslationRetryResponseDto> retryTranslation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String sessionId,
            @PathVariable Long segmentId
    ) {
        return ResponseUtil.ok(
                voiceSessionFacade.retryTranslation(
                        SecurityUtil.getLoginUserId(userPrincipal),
                        sessionId,
                        segmentId
                )
        );
    }
}
