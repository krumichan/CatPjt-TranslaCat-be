package jp.co.translacat.domain.accountbook.accountbook.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSummaryResponseDto;
import jp.co.translacat.domain.accountbook.accountbook.facade.AccountBookSummaryFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account-books/{accountBookId}/summary")
@RequiredArgsConstructor
@Validated
public class AccountBookSummaryController {

    private final AccountBookSummaryFacade accountBookSummaryFacade;

    @GetMapping
    public ResponseDto<AccountBookSummaryResponseDto> getSummary(
            @PathVariable Long accountBookId,
            @RequestParam(required = false) @Min(2000) @Max(9999) Integer year,
            @RequestParam(required = false) @Min(1) @Max(12) Integer month
    ) {
        return ResponseUtil.ok(
                accountBookSummaryFacade.getSummary(
                        accountBookId,
                        year,
                        month
                )
        );
    }
}