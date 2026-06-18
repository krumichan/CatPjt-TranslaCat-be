package jp.co.translacat.domain.currency.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.currency.dto.AdminCurrencyResponseDto;
import jp.co.translacat.domain.currency.dto.CurrencyCreateRequestDto;
import jp.co.translacat.domain.currency.dto.CurrencyEnabledUpdateRequestDto;
import jp.co.translacat.domain.currency.dto.CurrencyUpdateRequestDto;
import jp.co.translacat.domain.currency.service.CurrencyService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/currencies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCurrencyController {

    private final CurrencyService currencyService;

    @GetMapping
    @Operation(summary = "관리자 통화 목록 조회", description = "관리자가 전체 통화 목록을 조회한다.")
    public ResponseDto<List<AdminCurrencyResponseDto>> list() {
        return ResponseUtil.ok(currencyService.listAdminCurrencies());
    }

    @PostMapping
    @Operation(summary = "관리자 통화 등록", description = "관리자가 신규 통화를 등록한다.")
    public ResponseDto<AdminCurrencyResponseDto> create(
            @Valid @RequestBody CurrencyCreateRequestDto dto
    ) {
        return ResponseUtil.created(currencyService.create(dto));
    }

    @PutMapping("/{currencyId}")
    @Operation(
            summary = "관리자 통화 수정",
            description = "관리자가 통화 이름, 심볼, 소수점 자릿수를 수정한다."
    )
    public ResponseDto<AdminCurrencyResponseDto> update(
            @PathVariable Long currencyId,
            @Valid @RequestBody CurrencyUpdateRequestDto dto
    ) {
        return ResponseUtil.ok(currencyService.update(currencyId, dto));
    }

    @DeleteMapping("/{currencyId}")
    @Operation(
            summary = "관리자 통화 삭제",
            description = "관리자가 미사용 통화를 삭제한다."
    )
    public ResponseDto<Boolean> delete(
            @PathVariable Long currencyId
    ) {
        return ResponseUtil.ok(currencyService.delete(currencyId));
    }

    @PatchMapping("/{currencyId}/enabled")
    @Operation(summary = "관리자 통화 활성화 상태 변경", description = "관리자가 통화의 사용 여부를 변경한다.")
    public ResponseDto<AdminCurrencyResponseDto> updateEnabled(
            @PathVariable Long currencyId,
            @Valid @RequestBody CurrencyEnabledUpdateRequestDto dto
    ) {
        return ResponseUtil.ok(currencyService.updateEnabled(currencyId, dto));
    }

    @PatchMapping("/{currencyId}/base")
    @Operation(summary = "관리자 기본 통화 설정", description = "관리자가 특정 통화를 기본 통화로 설정한다.")
    public ResponseDto<AdminCurrencyResponseDto> setBaseCurrency(
            @PathVariable Long currencyId
    ) {
        return ResponseUtil.ok(currencyService.setBaseCurrency(currencyId));
    }
}