package jp.co.translacat.domain.chat.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.ai.dto.request.ChatRoomAiSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatRoomAiSettingResponseDto;
import jp.co.translacat.domain.chat.ai.service.ChatRoomAiSettingService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/rooms/{roomId}/ai-settings")
@RequiredArgsConstructor
public class ChatRoomAiSettingController {

    private final ChatRoomAiSettingService settingService;

    @GetMapping
    @Operation(summary = "채팅방 AI 설정 조회")
    public ResponseDto<ChatRoomAiSettingResponseDto> getSettings(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId
    ) {
        return ResponseUtil.ok(
                settingService.getSettings(
                        userPrincipal.getId(),
                        roomId
                )
        );
    }

    @PatchMapping
    @Operation(summary = "채팅방 AI 설정 수정")
    public ResponseDto<ChatRoomAiSettingResponseDto> updateSettings(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @RequestBody ChatRoomAiSettingUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                settingService.updateSettings(
                        userPrincipal.getId(),
                        roomId,
                        request
                )
        );
    }
}
