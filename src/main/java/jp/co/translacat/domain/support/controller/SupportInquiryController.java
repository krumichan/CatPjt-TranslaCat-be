package jp.co.translacat.domain.support.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.support.dto.*;
import jp.co.translacat.domain.support.enums.InquiryStatus;
import jp.co.translacat.domain.support.service.SupportInquiryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support/inquiries")
public class SupportInquiryController {

    private final SupportInquiryService inquiryService;

    @GetMapping
    public ResponseDto<SupportPageResponseDto<InquiryResponseDto>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseUtil.ok(inquiryService.list(principal.getId(), status, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseDto<InquiryResponseDto> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseUtil.ok(inquiryService.detail(principal.getId(), id));
    }

    @PostMapping
    public ResponseDto<InquiryResponseDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InquiryCreateRequestDto request
    ) {
        return ResponseUtil.created(inquiryService.create(principal.getId(), request));
    }

    @PostMapping("/{id}/replies")
    public ResponseDto<InquiryResponseDto> reply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ReplyCreateRequestDto request
    ) {
        return ResponseUtil.created(inquiryService.reply(principal.getId(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseDto<InquiryResponseDto> changeStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody InquiryStatusRequestDto request
    ) {
        return ResponseUtil.ok(inquiryService.changeStatus(principal.getId(), id, request));
    }
}
