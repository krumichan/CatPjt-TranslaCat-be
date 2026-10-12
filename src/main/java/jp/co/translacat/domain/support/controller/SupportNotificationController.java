package jp.co.translacat.domain.support.controller;

import jp.co.translacat.domain.support.dto.*;
import jp.co.translacat.domain.support.service.SupportNotificationService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support/notifications")
public class SupportNotificationController {

    private final SupportNotificationService notificationService;

    @GetMapping
    public ResponseDto<SupportPageResponseDto<NotificationResponseDto>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseUtil.ok(notificationService.list(principal.getId(), unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseDto<NotificationCountResponseDto> unreadCount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseUtil.ok(notificationService.unreadCount(principal.getId()));
    }

    @PatchMapping("/{id}/read")
    public ResponseDto<Boolean> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseUtil.ok(notificationService.markRead(principal.getId(), id));
    }

    @PatchMapping("/read-all")
    public ResponseDto<Boolean> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseUtil.ok(notificationService.markAllRead(principal.getId()));
    }
}
