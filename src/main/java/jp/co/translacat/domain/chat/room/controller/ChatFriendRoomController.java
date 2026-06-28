package jp.co.translacat.domain.chat.room.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomResponseDto;
import jp.co.translacat.domain.chat.room.facade.ChatRoomFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/friends")
@RequiredArgsConstructor
public class ChatFriendRoomController {

    private final ChatRoomFacade chatRoomFacade;

    @PostMapping("/{friendUserId}/direct-room")
    @Operation(
            summary = "친구 1:1 채팅방 시작",
            description = "친구 관계인 사용자와 FRIEND sourceType의 1:1 채팅방을 생성하거나 기존 방을 재사용한다."
    )
    public ResponseDto<ChatRoomResponseDto> createOrGetFriendDirectRoom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendUserId
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);

        return ResponseUtil.ok(chatRoomFacade.createOrGetFriendDirectRoom(
                loginUserId,
                friendUserId
        ));
    }
}
