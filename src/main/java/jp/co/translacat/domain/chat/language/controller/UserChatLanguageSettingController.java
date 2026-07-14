package jp.co.translacat.domain.chat.language.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.language.service.UserChatLanguageSettingService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/chat-language-settings")
@RequiredArgsConstructor
public class UserChatLanguageSettingController {

    private final UserChatLanguageSettingService userChatLanguageSettingService;

    @GetMapping
    @Operation(
            summary = "내 기본 채팅 언어 설정 조회",
            description = "로그인 사용자의 개인 기본 채팅 언어 설정을 조회한다. 저장된 설정이 없으면 시스템 기본값을 반환한다."
    )
    public ResponseDto getMyDefaultSetting(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                userChatLanguageSettingService.getMyDefaultSetting(
                        userPrincipal.getId()
                )
        );
    }

    @PatchMapping
    @Operation(
            summary = "내 기본 채팅 언어 설정 수정",
            description = "로그인 사용자의 개인 기본 채팅 언어 설정을 저장하거나 수정한다."
    )
    public ResponseDto updateMyDefaultSetting(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody ChatLanguageSettingUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                userChatLanguageSettingService.updateMyDefaultSetting(
                        userPrincipal.getId(),
                        request
                )
        );
    }
}
