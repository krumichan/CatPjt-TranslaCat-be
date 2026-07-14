package jp.co.translacat.domain.chat.language.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.language.service.ChatRoomLanguageSettingService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/rooms/{chatRoomId}/language-settings")
@RequiredArgsConstructor
public class ChatRoomLanguageSettingController {

    private final ChatRoomLanguageSettingService chatRoomLanguageSettingService;

    @GetMapping
    @Operation(
            summary = "채팅방 내 언어 설정 조회",
            description = "로그인 사용자의 해당 채팅방 전용 언어 설정을 조회한다. 방별 설정이 없으면 개인 기본값 또는 시스템 기본값을 반환한다."
    )
    public ResponseDto getMyRoomSetting(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId
    ) {
        return ResponseUtil.ok(
                chatRoomLanguageSettingService.getMyRoomSetting(
                        userPrincipal.getId(),
                        chatRoomId
                )
        );
    }

    @PatchMapping
    @Operation(
            summary = "채팅방 내 언어 설정 수정",
            description = "로그인 사용자의 해당 채팅방 전용 언어 설정만 수정한다."
    )
    public ResponseDto updateMyRoomSetting(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId,
            @RequestBody ChatLanguageSettingUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                chatRoomLanguageSettingService.updateMyRoomSetting(
                        userPrincipal.getId(),
                        chatRoomId,
                        request
                )
        );
    }

    @DeleteMapping
    @Operation(
            summary = "채팅방 내 언어 설정 초기화",
            description = "로그인 사용자의 해당 채팅방 전용 언어 설정을 제거하고 개인 기본값 또는 시스템 기본값을 다시 적용한다."
    )
    public ResponseDto resetMyRoomSetting(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId
    ) {
        return ResponseUtil.ok(
                chatRoomLanguageSettingService.resetMyRoomSetting(
                        userPrincipal.getId(),
                        chatRoomId
                )
        );
    }
}
