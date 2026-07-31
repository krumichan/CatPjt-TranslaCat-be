package jp.co.translacat.domain.chat.openchat.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatRoomCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomDetailResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomListResponseDto;
import jp.co.translacat.domain.chat.openchat.service.OpenChatRoomCommandService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatRoomQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/open-rooms")
@RequiredArgsConstructor
public class OpenChatRoomController {

    private final OpenChatRoomCommandService commandService;
    private final OpenChatRoomQueryService queryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "OPEN 채팅방 생성",
            description = "OPEN 채팅방과 OWNER 방별 프로필을 하나의 Transaction으로 생성한다."
    )
    public ResponseDto<OpenChatRoomDetailResponseDto> create(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody OpenChatRoomCreateRequestDto request
    ) {
        return ResponseUtil.created(
                commandService.create(
                        userPrincipal.getId(),
                        request
                )
        );
    }

    @GetMapping
    @Operation(
            summary = "PUBLIC OPEN 채팅방 목록·검색",
            description = "ACTIVE + PUBLIC OPEN 채팅방을 이름과 설명 기준으로 조회한다."
    )
    public ResponseDto<OpenChatRoomListResponseDto> getPublicRooms(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20")
            Integer size
    ) {
        return ResponseUtil.ok(
                queryService.getPublicRooms(
                        userPrincipal.getId(),
                        keyword,
                        cursorId,
                        size
                )
        );
    }

    @GetMapping("/{roomId}")
    @Operation(
            summary = "OPEN 채팅방 상세 조회",
            description = "PUBLIC 또는 직접 접근한 UNLISTED OPEN 채팅방 상세를 조회한다."
    )
    public ResponseDto<OpenChatRoomDetailResponseDto> getDetail(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId
    ) {
        return ResponseUtil.ok(
                queryService.getDetail(
                        userPrincipal.getId(),
                        roomId
                )
        );
    }
}
