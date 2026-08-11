package jp.co.translacat.domain.chat.message.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.chat.message.dto.request.ChatMessageCreateRequestDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageAnchorListResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageListResponseDto;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.service.ChatMessageCommandService;
import jp.co.translacat.domain.chat.message.service.ChatMessageQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/rooms/{chatRoomId}/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageQueryService chatMessageQueryService;
    private final ChatMessageCommandService chatMessageCommandService;

    @GetMapping
    @Operation(
            summary = "채팅방 메시지 목록 조회",
            description = "채팅방의 최신 메시지 100개 또는 cursorId 기준 이전 메시지 100개를 조회한다."
    )
    public ResponseDto<ChatMessageListResponseDto> getMessages(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long cursorId
    ) {
        return ResponseUtil.ok(
                chatMessageQueryService.getMessages(
                        userPrincipal.getId(),
                        chatRoomId,
                        cursorId
                )
        );
    }

    @GetMapping("/anchor")
    @Operation(
            summary = "채팅방 메시지 Anchor 구간 조회",
            description = "anchorMessageId를 기준으로 이전/이후 일부 메시지만 조회한다. first-unread 진입 시 대량 미읽음 전체를 조회하지 않기 위한 API다."
    )
    public ResponseDto<ChatMessageAnchorListResponseDto> getMessagesAroundAnchor(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId,
            @RequestParam Long anchorMessageId,
            @RequestParam(required = false) Integer beforeSize,
            @RequestParam(required = false) Integer afterSize
    ) {
        return ResponseUtil.ok(
                chatMessageQueryService.getMessagesAroundAnchor(
                        userPrincipal.getId(),
                        chatRoomId,
                        anchorMessageId,
                        beforeSize,
                        afterSize
                )
        );
    }

    @GetMapping("/after")
    @Operation(
            summary = "채팅방 메시지 이후 Page 조회",
            description = "cursorId 이후의 메시지를 오래된 순으로 조회한다. first-unread Anchor 진입 후 최신 방향 Pagination에 사용한다."
    )
    public ResponseDto<ChatMessageListResponseDto> getMessagesAfter(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId,
            @RequestParam Long cursorId,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseUtil.ok(
                chatMessageQueryService.getMessagesAfter(
                        userPrincipal.getId(),
                        chatRoomId,
                        cursorId,
                        size
                )
        );
    }

    @PostMapping
    @Operation(
            summary = "채팅 메시지 저장",
            description = "채팅방에 텍스트 메시지 원문을 저장한다. WebSocket 구현 전 REST 테스트용으로도 사용할 수 있다."
    )
    public ResponseDto<ChatMessageResponseDto> createTextMessage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatMessageCreateRequestDto request
    ) {
        return ResponseUtil.created(
                chatMessageCommandService.createTextMessage(
                        userPrincipal.getId(),
                        chatRoomId,
                        request
                )
        );
    }
}