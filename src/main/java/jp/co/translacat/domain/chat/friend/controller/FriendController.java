package jp.co.translacat.domain.chat.friend.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.friend.enums.FriendStatus;
import jp.co.translacat.domain.chat.friend.service.FriendService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/friends")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;

    @GetMapping
    @Operation(summary = "chat 친구 목록 조회", description = "Chat의 친구 목록을 조회한다.")
    public ResponseDto<?> friends(@RequestParam(value = "type", required = false) FriendStatus status) {
        return ResponseUtil.ok(this.friendService.friends(status == null ? FriendStatus.ACCEPTED : status));
    }
}
