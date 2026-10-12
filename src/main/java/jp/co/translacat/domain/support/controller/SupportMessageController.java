package jp.co.translacat.domain.support.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.support.dto.*;
import jp.co.translacat.domain.support.service.SupportMessageService;
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
@RequestMapping("/api/v1/support")
public class SupportMessageController {

    private final SupportMessageService messageService;

    @GetMapping("/messages")
    public ResponseDto<SupportPageResponseDto<MessageResponseDto>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseUtil.ok(messageService.list(principal.getId(), page, size));
    }

    @GetMapping("/messages/{id}")
    public ResponseDto<MessageResponseDto> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseUtil.ok(messageService.detail(principal.getId(), id));
    }

    @PostMapping("/messages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseDto<MessageResponseDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MessageCreateRequestDto request
    ) {
        return ResponseUtil.created(messageService.create(principal.getId(), request));
    }

    @GetMapping("/recipients")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseDto<List<SupportUserResponseDto>> recipients(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "") String query
    ) {
        return ResponseUtil.ok(messageService.recipients(principal.getId(), query));
    }
}
