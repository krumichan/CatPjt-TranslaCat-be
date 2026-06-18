package jp.co.translacat.domain.accountbook.receiptkeyword.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.receiptkeyword.dto.*;
import jp.co.translacat.domain.accountbook.receiptkeyword.service.AdminReceiptAiSettingService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReceiptAiSettingController {

    private final AdminReceiptAiSettingService adminReceiptAiSettingService;

    @GetMapping("/receipt-ocr-settings")
    @Operation(
            summary = "관리자 영수증 OCR 설정 목록 조회",
            description = "관리자가 통화별 영수증 OCR 언어 설정을 조회한다."
    )
    public ResponseDto<List<AdminReceiptOcrSettingResponseDto>> getOcrSettings() {
        return ResponseUtil.ok(adminReceiptAiSettingService.getOcrSettings());
    }

    @PutMapping("/receipt-ocr-settings/{settingId}")
    @Operation(
            summary = "관리자 영수증 OCR 설정 수정",
            description = "관리자가 통화별 영수증 OCR 언어 및 활성 상태를 수정한다."
    )
    public ResponseDto<AdminReceiptOcrSettingResponseDto> updateOcrSetting(
            @PathVariable Long settingId,
            @Valid @RequestBody AdminReceiptOcrSettingUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                adminReceiptAiSettingService.updateOcrSetting(
                        settingId,
                        request
                )
        );
    }

    @GetMapping("/receipt-keywords")
    @Operation(
            summary = "관리자 영수증 키워드 목록 조회",
            description = "관리자가 영수증 분석에 사용하는 키워드 목록을 조회한다."
    )
    public ResponseDto<List<AdminReceiptKeywordResponseDto>> getKeywords() {
        return ResponseUtil.ok(adminReceiptAiSettingService.getKeywords());
    }

    @PostMapping("/receipt-keywords")
    @Operation(
            summary = "관리자 영수증 키워드 등록",
            description = "관리자가 영수증 분석에 사용하는 키워드를 등록한다."
    )
    public ResponseDto<AdminReceiptKeywordResponseDto> createKeyword(
            @Valid @RequestBody AdminReceiptKeywordCreateRequestDto request
    ) {
        return ResponseUtil.created(
                adminReceiptAiSettingService.createKeyword(request)
        );
    }

    @PutMapping("/receipt-keywords/{keywordId}")
    @Operation(
            summary = "관리자 영수증 키워드 수정",
            description = "관리자가 영수증 분석에 사용하는 키워드를 수정한다."
    )
    public ResponseDto<AdminReceiptKeywordResponseDto> updateKeyword(
            @PathVariable Long keywordId,
            @Valid @RequestBody AdminReceiptKeywordUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                adminReceiptAiSettingService.updateKeyword(
                        keywordId,
                        request
                )
        );
    }

    @DeleteMapping("/receipt-keywords/{keywordId}")
    @Operation(
            summary = "관리자 영수증 키워드 삭제",
            description = "관리자가 영수증 분석에 사용하는 키워드를 삭제한다."
    )
    public ResponseDto<Boolean> deleteKeyword(
            @PathVariable Long keywordId
    ) {
        return ResponseUtil.ok(
                adminReceiptAiSettingService.deleteKeyword(keywordId)
        );
    }
}