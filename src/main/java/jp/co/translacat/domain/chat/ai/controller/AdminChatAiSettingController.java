package jp.co.translacat.domain.chat.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.ai.dto.request.ChatAiSystemSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiSystemSettingResponseDto;
import jp.co.translacat.domain.chat.ai.service.ChatAiSystemSettingService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/chat/ai-settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatAiSettingController {

    private final ChatAiSystemSettingService settingService;

    @GetMapping
    @Operation(summary = "시스템 관리자 AI 채팅 운영 설정 조회")
    public ResponseDto<ChatAiSystemSettingResponseDto> getSettings() {
        return ResponseUtil.ok(settingService.getSettings());
    }

    @PatchMapping
    @Operation(summary = "시스템 관리자 AI 채팅 운영 설정 수정")
    public ResponseDto<ChatAiSystemSettingResponseDto> updateSettings(
            @RequestBody ChatAiSystemSettingUpdateRequestDto request
    ) {
        return ResponseUtil.ok(settingService.updateSettings(request));
    }
}
