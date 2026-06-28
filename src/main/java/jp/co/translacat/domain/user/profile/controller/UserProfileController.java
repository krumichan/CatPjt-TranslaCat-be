package jp.co.translacat.domain.user.profile.controller;

import jp.co.translacat.domain.user.profile.dto.UserProfileResponseDto;
import jp.co.translacat.domain.user.profile.dto.UserProfileUpdateRequestDto;
import jp.co.translacat.domain.user.profile.service.UserProfileService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseDto<UserProfileResponseDto> getMyProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(userProfileService.getMyProfile(loginUserId));
    }

    @PatchMapping
    public ResponseDto<UserProfileResponseDto> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody UserProfileUpdateRequestDto request
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(userProfileService.updateMyProfile(loginUserId, request));
    }
}
