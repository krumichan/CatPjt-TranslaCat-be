package jp.co.translacat.domain.currency.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.currency.dto.CurrencyResponseDto;
import jp.co.translacat.domain.currency.service.CurrencyService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/currencies")
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping
    @Operation(summary = "통화 목록 조회", description = "가계부 등록에 사용할 수 있는 통화 목록을 조회한다.")
    public ResponseDto<List<CurrencyResponseDto>> list() {
        return ResponseUtil.ok(currencyService.listEnabledCurrencies());
    }
}