package jp.co.translacat.domain.chat.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityItemResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityListResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityReadAllResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationChatListResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationSummaryResponseDto;
import jp.co.translacat.domain.chat.notification.service.ChatNotificationActivityCommandService;
import jp.co.translacat.domain.chat.notification.service.ChatNotificationQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/notifications")
@RequiredArgsConstructor
public class ChatNotificationController {

    private final ChatNotificationQueryService queryService;
    private final ChatNotificationActivityCommandService activityCommandService;

    @GetMapping("/summary")
    @Operation(
            summary = "채팅 알림 Summary 조회",
            description = "채팅 미읽음 Message/Room 수와 활동 미읽음 수를 조회한다."
    )
    public ResponseDto<ChatNotificationSummaryResponseDto> getSummary(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                queryService.getSummary(userPrincipal.getId())
        );
    }

    @GetMapping("/chats")
    @Operation(
            summary = "미읽음 채팅 알림 목록 조회",
            description = "Room 단위로 마지막 Message, 미읽음 수, firstUnreadMessageId를 조회한다."
    )
    public ResponseDto<ChatNotificationChatListResponseDto> getUnreadChats(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) Long cursorMessageId,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        return ResponseUtil.ok(
                queryService.getUnreadChats(
                        userPrincipal.getId(),
                        cursorMessageId,
                        size
                )
        );
    }

    @GetMapping("/activities")
    @Operation(
            summary = "활동 알림 목록 조회",
            description = "GROUP 초대, OPEN 강퇴/Role 변경/Room 종료 활동 이력을 Cursor 방식으로 조회한다."
    )
    public ResponseDto<ChatNotificationActivityListResponseDto> getActivities(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "false") Boolean onlyUnread,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        return ResponseUtil.ok(
                queryService.getActivities(
                        userPrincipal.getId(),
                        onlyUnread,
                        cursorId,
                        size
                )
        );
    }

    @PatchMapping("/activities/{notificationId}/read")
    @Operation(
            summary = "활동 알림 단건 읽음 처리",
            description = "로그인 사용자 본인의 활동 알림을 멱등하게 읽음 처리한다."
    )
    public ResponseDto<ChatNotificationActivityItemResponseDto> markActivityAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long notificationId
    ) {
        return ResponseUtil.ok(
                activityCommandService.markAsRead(
                        userPrincipal.getId(),
                        notificationId
                )
        );
    }

    @PatchMapping("/activities/read-all")
    @Operation(
            summary = "활동 알림 전체 읽음 처리",
            description = "로그인 사용자의 미읽음 활동 알림을 모두 읽음 처리한다. Chat Room unread에는 영향을 주지 않는다."
    )
    public ResponseDto<ChatNotificationActivityReadAllResponseDto> markAllActivitiesAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseUtil.ok(
                activityCommandService.markAllAsRead(
                        userPrincipal.getId()
                )
        );
    }
}
