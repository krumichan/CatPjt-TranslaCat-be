package jp.co.translacat.domain.user.profile.controller;

import jp.co.translacat.domain.user.profile.dto.UserProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileImageService;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageUploadFile;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserProfileImageController {

    private final UserProfileImageService userProfileImageService;

    @PostMapping(
            value = "/profile-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseDto<UserProfileResponseDto> uploadProfileImage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestPart("file") MultipartFile file
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);

        return ResponseUtil.ok(
                userProfileImageService.uploadProfileImage(
                        loginUserId,
                        toUploadFile(file)
                )
        );
    }

    @DeleteMapping("/profile-image")
    public ResponseDto<UserProfileResponseDto> deleteProfileImage(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);

        return ResponseUtil.ok(
                userProfileImageService.deleteProfileImage(loginUserId)
        );
    }

    @PostMapping(
            value = "/profile-background-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseDto<UserProfileResponseDto> uploadProfileBackgroundImage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestPart("file") MultipartFile file
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);

        return ResponseUtil.ok(
                userProfileImageService.uploadProfileBackgroundImage(
                        loginUserId,
                        toUploadFile(file)
                )
        );
    }

    @DeleteMapping("/profile-background-image")
    public ResponseDto<UserProfileResponseDto> deleteProfileBackgroundImage(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);

        return ResponseUtil.ok(
                userProfileImageService.deleteProfileBackgroundImage(
                        loginUserId
                )
        );
    }

    private ProfileImageUploadFile toUploadFile(MultipartFile file) {
        try {
            return new ProfileImageUploadFile(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException e) {
            throw new BusinessException(
                    "이미지 파일을 읽을 수 없습니다.",
                    "PROFILE_IMAGE_READ_FAILED"
            );
        }
    }
}
