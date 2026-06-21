package jp.co.translacat.domain.chat.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.chat.member.dto.request.ChatRoomLanguageSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomLanguageSettingResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberListResponseDto;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberCommandService;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/rooms/{chatRoomId}/members")
@RequiredArgsConstructor
public class ChatRoomMemberController {

    private final ChatRoomMemberQueryService chatRoomMemberQueryService;
    private final ChatRoomMemberCommandService chatRoomMemberCommandService;

    @GetMapping
    @Operation(
            summary = "채팅방 멤버 목록 조회",
            description = "로그인 사용자가 참여 중인 채팅방의 멤버 목록을 조회한다."
    )
    public ResponseDto<ChatRoomMemberListResponseDto> getMembers(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId
    ) {
        return ResponseUtil.ok(
                chatRoomMemberQueryService.getMembers(
                        userPrincipal.getId(),
                        chatRoomId
                )
        );
    }

    @GetMapping("/me/language")
    @Operation(
            summary = "내 채팅방 언어 설정 조회",
            description = "로그인 사용자의 해당 채팅방 언어 설정을 조회한다."
    )
    public ResponseDto<ChatRoomLanguageSettingResponseDto> getMyLanguageSetting(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId
    ) {
        return ResponseUtil.ok(
                chatRoomMemberQueryService.getMyLanguageSetting(
                        userPrincipal.getId(),
                        chatRoomId
                )
        );
    }

    @PatchMapping("/me/language")
    @Operation(
            summary = "내 채팅방 언어 설정 변경",
            description = "로그인 사용자의 해당 채팅방 한정 언어 설정을 변경한다."
    )
    public ResponseDto<ChatRoomLanguageSettingResponseDto> updateMyLanguageSetting(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatRoomLanguageSettingUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                chatRoomMemberCommandService.updateMyLanguageSetting(
                        userPrincipal.getId(),
                        chatRoomId,
                        request
                )
        );
    }

    @DeleteMapping("/me/language")
    @Operation(
            summary = "내 채팅방 언어 설정 초기화",
            description = "로그인 사용자의 해당 채팅방 언어 설정을 초기화하고 기본 언어 설정을 따르도록 되돌린다."
    )
    public ResponseDto<ChatRoomLanguageSettingResponseDto> resetMyLanguageSetting(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId
    ) {
        return ResponseUtil.ok(
                chatRoomMemberCommandService.resetMyLanguageSetting(
                        userPrincipal.getId(),
                        chatRoomId
                )
        );
    }
}