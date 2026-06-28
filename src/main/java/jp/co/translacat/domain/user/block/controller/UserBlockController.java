package jp.co.translacat.domain.user.block.controller;

import jp.co.translacat.domain.user.block.dto.UserBlockRequestDto;
import jp.co.translacat.domain.user.block.dto.UserBlockResponseDto;
import jp.co.translacat.domain.user.block.service.UserBlockService;
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
@RequestMapping("/api/v1/blocks")
public class UserBlockController {

    private final UserBlockService userBlockService;

    @PostMapping
    public ResponseDto<UserBlockResponseDto> blockUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody UserBlockRequestDto request
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(userBlockService.blockUser(loginUserId, request));
    }

    @GetMapping
    public ResponseDto<List<UserBlockResponseDto>> getBlockedUsers(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(userBlockService.getBlockedUsers(loginUserId));
    }

    @DeleteMapping("/{blockedUserId}")
    public ResponseDto<Boolean> unblockUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long blockedUserId
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        userBlockService.unblockUser(loginUserId, blockedUserId);
        return ResponseUtil.ok(true);
    }
}
