package jp.co.translacat.domain.chat.openchat.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatJoinRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatOwnerTransferRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatProfileUpdateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatRoomCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberListResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMembershipResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomDetailResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomListResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatMembershipService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatRoomCommandService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatRoomQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/open-rooms")
@RequiredArgsConstructor
public class OpenChatRoomController {

    private final OpenChatRoomCommandService commandService;
    private final OpenChatRoomQueryService queryService;
    private final OpenChatMembershipService membershipService;
    private final OpenChatProfileService profileService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "OPEN 채팅방 생성",
            description = "OPEN 채팅방과 OWNER 방별 프로필을 하나의 Transaction으로 생성한다."
    )
    public ResponseDto<OpenChatRoomDetailResponseDto> create(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody OpenChatRoomCreateRequestDto request
    ) {
        return ResponseUtil.created(
                commandService.create(
                        userPrincipal.getId(),
                        request
                )
        );
    }

    @GetMapping
    @Operation(
            summary = "PUBLIC OPEN 채팅방 목록·검색",
            description = "ACTIVE + PUBLIC OPEN 채팅방을 이름과 설명 기준으로 조회한다."
    )
    public ResponseDto<OpenChatRoomListResponseDto> getPublicRooms(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        return ResponseUtil.ok(
                queryService.getPublicRooms(
                        userPrincipal.getId(),
                        keyword,
                        cursorId,
                        size
                )
        );
    }

    @GetMapping("/{roomId}")
    @Operation(
            summary = "OPEN 채팅방 상세 조회",
            description = "PUBLIC 또는 직접 접근한 UNLISTED OPEN 채팅방 상세를 조회한다."
    )
    public ResponseDto<OpenChatRoomDetailResponseDto> getDetail(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId
    ) {
        return ResponseUtil.ok(
                queryService.getDetail(
                        userPrincipal.getId(),
                        roomId
                )
        );
    }

    @PostMapping("/{roomId}/join")
    @Operation(
            summary = "OPEN 채팅방 참여·재참여",
            description = "정원과 방 상태를 Lock 안에서 재검증하고 방별 OPEN 프로필로 참여한다."
    )
    public ResponseDto<OpenChatRoomDetailResponseDto> join(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @RequestBody(required = false) OpenChatJoinRequestDto request
    ) {
        return ResponseUtil.ok(
                membershipService.join(
                        userPrincipal.getId(),
                        roomId,
                        request
                )
        );
    }

    @DeleteMapping("/{roomId}/leave")
    @Operation(
            summary = "OPEN 채팅방 일반 퇴실",
            description = "멤버십을 비활성화하되 방별 프로필과 memberCode는 유지한다."
    )
    public ResponseDto<OpenChatMembershipResponseDto> leave(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId
    ) {
        return ResponseUtil.ok(
                membershipService.leave(
                        userPrincipal.getId(),
                        roomId
                )
        );
    }

    @PostMapping("/{roomId}/owner-transfer")
    @Operation(
            summary = "OPEN 채팅방 OWNER 위임",
            description = "현재 OWNER가 활성 MEMBER 또는 ADMIN에게 OWNER 역할을 원자적으로 위임한다."
    )
    public ResponseDto<OpenChatRoomDetailResponseDto> transferOwner(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @RequestBody OpenChatOwnerTransferRequestDto request
    ) {
        return ResponseUtil.ok(
                membershipService.transferOwner(
                        userPrincipal.getId(),
                        roomId,
                        request
                )
        );
    }

    @PostMapping("/{roomId}/close")
    @Operation(
            summary = "OPEN 채팅방 종료",
            description = "OWNER가 OPEN 채팅방을 CLOSED로 전환하고 Commit 이후 종료 Event를 발행한다."
    )
    public ResponseDto<OpenChatRoomDetailResponseDto> close(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId
    ) {
        return ResponseUtil.ok(
                membershipService.close(
                        userPrincipal.getId(),
                        roomId
                )
        );
    }

    @GetMapping("/{roomId}/me/profile")
    @Operation(summary = "내 OPEN 프로필 조회")
    public ResponseDto<OpenChatMemberProfileResponseDto> getMyProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId
    ) {
        return ResponseUtil.ok(
                profileService.getMyProfile(
                        userPrincipal.getId(),
                        roomId
                )
        );
    }

    @PatchMapping("/{roomId}/me/profile")
    @Operation(
            summary = "내 OPEN 프로필 수정",
            description = "방별 닉네임을 수정하고 Commit 이후 chat.open-profile.updated Event를 발행한다."
    )
    public ResponseDto<OpenChatMemberProfileResponseDto> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @RequestBody OpenChatProfileUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                profileService.updateMyProfile(
                        userPrincipal.getId(),
                        roomId,
                        request
                )
        );
    }

    @GetMapping("/{roomId}/members")
    @Operation(summary = "OPEN 채팅방 활성 멤버 목록 조회")
    public ResponseDto<OpenChatMemberListResponseDto> getMembers(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId
    ) {
        return ResponseUtil.ok(
                profileService.getMembers(
                        userPrincipal.getId(),
                        roomId
                )
        );
    }

    @GetMapping("/{roomId}/members/{openChatMemberId}")
    @Operation(summary = "OPEN 채팅방 멤버 프로필 조회")
    public ResponseDto<OpenChatMemberProfileResponseDto> getMemberProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long roomId,
            @PathVariable Long openChatMemberId
    ) {
        return ResponseUtil.ok(
                profileService.getMemberProfile(
                        userPrincipal.getId(),
                        roomId,
                        openChatMemberId
                )
        );
    }
}
