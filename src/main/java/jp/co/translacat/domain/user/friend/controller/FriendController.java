package jp.co.translacat.domain.user.friend.controller;

import jp.co.translacat.domain.user.friend.dto.FriendResponseDto;
import jp.co.translacat.domain.user.friend.service.FriendService;
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
@RequestMapping("/api/v1/friends")
public class FriendController {

    private final FriendService friendService;

    @GetMapping
    public ResponseDto<List<FriendResponseDto>> getFriends(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(friendService.getFriends(loginUserId));
    }

    @DeleteMapping("/{friendUserId}")
    public ResponseDto<Boolean> deleteFriend(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendUserId
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        friendService.deleteFriend(loginUserId, friendUserId);
        return ResponseUtil.ok(true);
    }
}
