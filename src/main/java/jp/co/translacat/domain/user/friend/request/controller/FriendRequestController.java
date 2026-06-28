package jp.co.translacat.domain.user.friend.request.controller;

import jp.co.translacat.domain.user.friend.request.dto.FriendRequestListItemResponseDto;
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

import java.util.List;

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

    @GetMapping("/received")
    public ResponseDto<List<FriendRequestListItemResponseDto>> getReceivedPendingRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(friendRequestService.getReceivedPendingRequests(loginUserId));
    }

    @GetMapping("/sent")
    public ResponseDto<List<FriendRequestListItemResponseDto>> getSentPendingRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(friendRequestService.getSentPendingRequests(loginUserId));
    }

    @PatchMapping("/{requestId}/accept")
    public ResponseDto<FriendRequestListItemResponseDto> acceptFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long requestId
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(friendRequestService.acceptFriendRequest(loginUserId, requestId));
    }

    @PatchMapping("/{requestId}/reject")
    public ResponseDto<FriendRequestListItemResponseDto> rejectFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long requestId
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(friendRequestService.rejectFriendRequest(loginUserId, requestId));
    }

    @PatchMapping("/{requestId}/cancel")
    public ResponseDto<FriendRequestListItemResponseDto> cancelFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long requestId
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(friendRequestService.cancelFriendRequest(loginUserId, requestId));
    }
}
