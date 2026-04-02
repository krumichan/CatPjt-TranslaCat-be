package jp.co.translacat.domain.chat.friend.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.friend.dto.ReqUpdateProfileDto;
import jp.co.translacat.domain.chat.friend.dto.RespChatFriendDto;
import jp.co.translacat.domain.chat.friend.enums.FriendStatus;
import jp.co.translacat.domain.chat.friend.service.FriendService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat/friends")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;

    @GetMapping("/types")
    @Operation(summary = "친구 타입 목록 조회", description = "친구 타입 목록을 조회한다.")
    public ResponseDto<List<FriendStatus>> types() {
        return ResponseUtil.ok(List.of(FriendStatus.values()));
    }

    @GetMapping
    @Operation(summary = "chat 친구 목록 조회", description = "Chat의 친구 목록을 조회한다.")
    public ResponseDto<List<RespChatFriendDto>> friends(@RequestParam(value = "type", required = false) FriendStatus status) {
        return ResponseUtil.ok(this.friendService.friends(status == null ? FriendStatus.ACCEPTED : status));
    }

    @GetMapping("/check-activation")
    @Operation(summary = "활성화 여부 확인", description = "현재 사용자의 chat가 활성화되어 있는지 확인한다.")
    public ResponseDto<Boolean> checkActivation() {
        return ResponseUtil.ok(this.friendService.checkActivation());
    }

    @PostMapping("/update-profile")
    @Operation(summary = "프로필 수정", description = "프로필을 수정한다.")
    public ResponseDto<Boolean> updateProfile(@ModelAttribute ReqUpdateProfileDto requestDto) {
        return ResponseUtil.ok(this.friendService.updateProfile(requestDto));
    }
}
