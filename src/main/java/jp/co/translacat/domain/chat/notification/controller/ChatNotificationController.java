package jp.co.translacat.domain.chat.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationChatListResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationSummaryResponseDto;
import jp.co.translacat.domain.chat.notification.service.ChatNotificationQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/notifications")
@RequiredArgsConstructor
public class ChatNotificationController {

    private final ChatNotificationQueryService queryService;

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
}
