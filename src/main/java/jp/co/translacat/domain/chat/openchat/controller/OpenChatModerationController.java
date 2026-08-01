package jp.co.translacat.domain.chat.openchat.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatBanCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatBanActionResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatBanListResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.service.OpenChatBanQueryService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatModerationService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/open-rooms/{roomId}")
@RequiredArgsConstructor
public class OpenChatModerationController {

    private final OpenChatModerationService moderationService;
    private final OpenChatBanQueryService banQueryService;

    @PostMapping("/admins/{openChatMemberId}")
    @Operation(summary = "OPEN ADMIN 지정")
    public ResponseDto<OpenChatMemberProfileResponseDto> assignAdmin(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long openChatMemberId
    ) {
        return ResponseUtil.ok(
                moderationService.assignAdmin(
                        userPrincipal.getId(),
                        roomId,
                        openChatMemberId
                )
        );
    }

    @DeleteMapping("/admins/{openChatMemberId}")
    @Operation(summary = "OPEN ADMIN 해제")
    public ResponseDto<OpenChatMemberProfileResponseDto> revokeAdmin(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long openChatMemberId
    ) {
        return ResponseUtil.ok(
                moderationService.revokeAdmin(
                        userPrincipal.getId(),
                        roomId,
                        openChatMemberId
                )
        );
    }

    @PostMapping("/bans")
    @Operation(summary = "OPEN 멤버 강제 퇴장 및 방 단위 블랙리스트 등록")
    public ResponseDto<OpenChatBanActionResponseDto> ban(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @Valid @RequestBody OpenChatBanCreateRequestDto request
    ) {
        return ResponseUtil.ok(
                moderationService.ban(
                        userPrincipal.getId(),
                        roomId,
                        request
                )
        );
    }

    @GetMapping("/bans")
    @Operation(summary = "OPEN 활성 블랙리스트 목록·검색")
    public ResponseDto<OpenChatBanListResponseDto> getActiveBans(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, name = "cursor") Long cursorId,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        return ResponseUtil.ok(
                banQueryService.getActiveBans(
                        userPrincipal.getId(),
                        roomId,
                        keyword,
                        cursorId,
                        size
                )
        );
    }

    @PatchMapping("/bans/{banId}/release")
    @Operation(summary = "OPEN 블랙리스트 해제")
    public ResponseDto<OpenChatBanActionResponseDto> release(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long banId
    ) {
        return ResponseUtil.ok(
                moderationService.release(
                        userPrincipal.getId(),
                        roomId,
                        banId
                )
        );
    }
}
