package jp.co.translacat.domain.chat.room.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.chat.room.dto.request.ChatRoomCreateRequestDto;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomListResponseDto;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomResponseDto;
import jp.co.translacat.domain.chat.room.facade.ChatRoomFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomFacade chatRoomFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "채팅방 생성",
            description = "1:1 채팅방 또는 그룹 채팅방을 생성한다."
    )
    public ResponseDto<ChatRoomResponseDto> create(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ChatRoomCreateRequestDto request
    ) {
        return ResponseUtil.created(
                chatRoomFacade.create(
                        userPrincipal.getId(),
                        request
                )
        );
    }

    @GetMapping
    @Operation(
            summary = "내 채팅방 목록 조회",
            description = "로그인 사용자가 참여 중인 채팅방 목록을 조회한다."
    )
    public ResponseDto<ChatRoomListResponseDto> getMyChatRooms(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                chatRoomFacade.getMyChatRooms(userPrincipal.getId())
        );
    }

    @GetMapping("/{chatRoomId}")
    @Operation(
            summary = "채팅방 상세 조회",
            description = "로그인 사용자가 참여 중인 특정 채팅방의 상세 정보를 조회한다."
    )
    public ResponseDto<ChatRoomResponseDto> getChatRoom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId
    ) {
        return ResponseUtil.ok(
                chatRoomFacade.getChatRoom(
                        userPrincipal.getId(),
                        chatRoomId
                )
        );
    }
}