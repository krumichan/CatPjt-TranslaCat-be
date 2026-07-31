package jp.co.translacat.domain.chat.read.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.chat.read.dto.request.ChatRoomReadRequestDto;
import jp.co.translacat.domain.chat.read.service.ChatRoomReadService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomReadController {

    private final ChatRoomReadService chatRoomReadService;

    @PatchMapping("/{chatRoomId}/read")
    @Operation(
            summary = "채팅방 읽음 처리",
            description = "지정한 메시지까지 사용자의 읽음 커서를 단조 증가시킨다."
    )
    public ResponseDto markAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatRoomReadRequestDto request
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);

        return ResponseUtil.ok(
                chatRoomReadService.markAsRead(
                        loginUserId,
                        chatRoomId,
                        request
                )
        );
    }
}
