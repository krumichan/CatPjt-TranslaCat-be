package jp.co.translacat.domain.support.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.support.dto.*;
import jp.co.translacat.domain.support.service.SupportNoticeService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support/notices")
public class SupportNoticeController {

    private final SupportNoticeService noticeService;

    @GetMapping("/urgent")
    public ResponseDto<List<NoticeResponseDto>> pendingUrgent(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseUtil.ok(noticeService.pendingUrgent(principal.getId()));
    }

    @PutMapping("/{id}/dismiss")
    public ResponseDto<Boolean> dismiss(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseUtil.ok(noticeService.dismiss(principal.getId(), id));
    }

    @GetMapping
    public ResponseDto<SupportPageResponseDto<NoticeResponseDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseUtil.ok(noticeService.list(page, size));
    }

    @GetMapping("/{id}")
    public ResponseDto<NoticeResponseDto> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseUtil.ok(noticeService.detail(principal.getId(), id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseDto<NoticeResponseDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody NoticeWriteRequestDto request
    ) {
        return ResponseUtil.created(noticeService.create(principal.getId(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseDto<NoticeResponseDto> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody NoticeWriteRequestDto request
    ) {
        return ResponseUtil.ok(noticeService.update(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseDto<Boolean> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseUtil.ok(noticeService.delete(principal.getId(), id));
    }
}
