package jp.co.translacat.domain.chat.ai.controller;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiMemberResponseDto;
import jp.co.translacat.domain.chat.ai.service.ChatAiProfileImageService;
import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageUploadFile;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms/{roomId}/ai-members/{aiMemberId}")
public class ChatAiProfileImageController {

    private final ChatAiProfileImageService imageService;

    @PostMapping(
            value = "/profile-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseDto<ChatAiMemberResponseDto> uploadProfileImage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long aiMemberId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseUtil.ok(
                imageService.uploadProfileImage(
                        userPrincipal.getId(),
                        roomId,
                        aiMemberId,
                        toUploadFile(file)
                )
        );
    }

    @DeleteMapping("/profile-image")
    public ResponseDto<ChatAiMemberResponseDto> deleteProfileImage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long aiMemberId
    ) {
        return ResponseUtil.ok(
                imageService.deleteProfileImage(
                        userPrincipal.getId(),
                        roomId,
                        aiMemberId
                )
        );
    }

    @PostMapping(
            value = "/profile-background-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseDto<ChatAiMemberResponseDto> uploadBackgroundImage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long aiMemberId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseUtil.ok(
                imageService.uploadBackgroundImage(
                        userPrincipal.getId(),
                        roomId,
                        aiMemberId,
                        toUploadFile(file)
                )
        );
    }

    @DeleteMapping("/profile-background-image")
    public ResponseDto<ChatAiMemberResponseDto> deleteBackgroundImage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long aiMemberId
    ) {
        return ResponseUtil.ok(
                imageService.deleteBackgroundImage(
                        userPrincipal.getId(),
                        roomId,
                        aiMemberId
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
                    "AI 프로필 이미지 파일을 읽을 수 없습니다.",
                    ChatAiErrorCode.PROFILE_IMAGE_READ_FAILED
            );
        }
    }
}
