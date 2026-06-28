package jp.co.translacat.domain.user.friend.request.controller;

import jp.co.translacat.domain.user.friend.request.dto.FriendRequestResponseDto;
import jp.co.translacat.domain.user.friend.request.dto.FriendRequestSendRequestDto;
import jp.co.translacat.domain.user.friend.request.service.FriendRequestService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/friend-requests")
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    @PostMapping
    public ResponseDto<FriendRequestResponseDto> sendFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody FriendRequestSendRequestDto request
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(friendRequestService.sendFriendRequest(loginUserId, request));
    }
}
