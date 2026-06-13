package jp.co.translacat.domain.accountbook.chart.controller;

import jp.co.translacat.domain.accountbook.chart.dto.AccountBookMonthlyChartResponseDto;
import jp.co.translacat.domain.accountbook.chart.dto.AccountBookRankingChartResponseDto;
import jp.co.translacat.domain.accountbook.chart.service.AccountBookChartService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Year;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account-books/{accountBookId}/charts")
public class AccountBookChartController {

    private final AccountBookChartService accountBookChartService;

    @GetMapping("/monthly")
    public ResponseDto<AccountBookMonthlyChartResponseDto> getMonthlyChart(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @RequestParam(required = false) Integer year
    ) {
        int targetYear = year != null ? year : Year.now().getValue();

        return ResponseUtil.ok(
                accountBookChartService.getMonthlyChart(
                        accountBookId,
                        targetYear,
                        userPrincipal.getId()
                )
        );
    }

    @GetMapping("/categories")
    public ResponseDto<AccountBookRankingChartResponseDto> getCategoryChart(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return ResponseUtil.ok(
                accountBookChartService.getCategoryChart(
                        accountBookId,
                        year,
                        month,
                        userPrincipal.getId()
                )
        );
    }

    @GetMapping("/stores")
    public ResponseDto<AccountBookRankingChartResponseDto> getStoreChart(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return ResponseUtil.ok(
                accountBookChartService.getStoreChart(
                        accountBookId,
                        year,
                        month,
                        userPrincipal.getId()
                )
        );
    }
}