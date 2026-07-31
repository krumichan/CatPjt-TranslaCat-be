package jp.co.translacat.domain.chat.openchat.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileImageService;
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
@RequestMapping("/api/v1/chat/open-rooms/{roomId}/me/profile-image")
public class OpenChatProfileImageController {

    private final OpenChatProfileImageService imageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "내 OPEN 프로필 이미지 업로드·교체")
    public ResponseDto<OpenChatMemberProfileResponseDto> upload(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseUtil.ok(
                imageService.upload(
                        userPrincipal.getId(),
                        roomId,
                        toUploadFile(file)
                )
        );
    }

    @DeleteMapping
    @Operation(summary = "내 OPEN 프로필 이미지 삭제")
    public ResponseDto<OpenChatMemberProfileResponseDto> delete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId
    ) {
        return ResponseUtil.ok(
                imageService.delete(
                        userPrincipal.getId(),
                        roomId
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
                    "OPEN 프로필 이미지 파일을 읽을 수 없습니다.",
                    "OPEN_CHAT_PROFILE_IMAGE_READ_FAILED"
            );
        }
    }
}
