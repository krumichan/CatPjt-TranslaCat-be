package jp.co.translacat.domain.chat.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.chat.ai.dto.request.ChatAiMemberCreateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.request.ChatAiMemberUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiMemberListResponseDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiMemberResponseDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiSafeProfileResponseDto;
import jp.co.translacat.domain.chat.ai.service.ChatAiDisplayMemberService;
import jp.co.translacat.domain.chat.ai.service.ChatAiMemberService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/rooms/{roomId}/ai-members")
@RequiredArgsConstructor
public class ChatAiMemberController {

    private final ChatAiMemberService aiMemberService;
    private final ChatAiDisplayMemberService aiDisplayMemberService;

    @GetMapping
    @Operation(summary = "채팅방 AI 멤버 목록 조회")
    public ResponseDto<ChatAiMemberListResponseDto> getMembers(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId
    ) {
        return ResponseUtil.ok(
                aiMemberService.getMembers(
                        userPrincipal.getId(),
                        roomId
                )
        );
    }

    @PostMapping
    @Operation(summary = "채팅방 AI 멤버 추가")
    public ResponseDto<ChatAiMemberResponseDto> create(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @Valid @RequestBody ChatAiMemberCreateRequestDto request
    ) {
        return ResponseUtil.created(
                aiMemberService.create(
                        userPrincipal.getId(),
                        roomId,
                        request
                )
        );
    }

    @GetMapping("/{aiMemberId}")
    @Operation(summary = "채팅방 AI 멤버 상세 조회")
    public ResponseDto<ChatAiMemberResponseDto> getMember(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long aiMemberId
    ) {
        return ResponseUtil.ok(
                aiMemberService.getMember(
                        userPrincipal.getId(),
                        roomId,
                        aiMemberId
                )
        );
    }

    @GetMapping("/{aiMemberId}/profile")
    @Operation(summary = "채팅방 AI 멤버 공개 프로필 조회")
    public ResponseDto<ChatAiSafeProfileResponseDto> getMemberProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long aiMemberId
    ) {
        return ResponseUtil.ok(
                aiDisplayMemberService.getSafeProfile(
                        userPrincipal.getId(),
                        roomId,
                        aiMemberId
                )
        );
    }

    @PatchMapping("/{aiMemberId}")
    @Operation(summary = "채팅방 AI 멤버 수정")
    public ResponseDto<ChatAiMemberResponseDto> update(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long aiMemberId,
            @Valid @RequestBody ChatAiMemberUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                aiMemberService.update(
                        userPrincipal.getId(),
                        roomId,
                        aiMemberId,
                        request
                )
        );
    }

    @DeleteMapping("/{aiMemberId}")
    @Operation(summary = "채팅방 AI 멤버 삭제")
    public ResponseDto<ChatAiMemberResponseDto> delete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long aiMemberId
    ) {
        return ResponseUtil.ok(
                aiMemberService.delete(
                        userPrincipal.getId(),
                        roomId,
                        aiMemberId
                )
        );
    }
}
