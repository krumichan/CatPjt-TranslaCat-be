package jp.co.translacat.domain.accountbook.transaction.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionListResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionMonthResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionRequestDto;
import jp.co.translacat.domain.accountbook.transaction.query.AccountBookTransactionQueryService;
import jp.co.translacat.domain.accountbook.transaction.service.AccountBookTransactionService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account-books/{accountBookId}/transactions")
@RequiredArgsConstructor
public class AccountBookTransactionController {

    private final AccountBookTransactionService accountBookTransactionService;
    private final AccountBookTransactionQueryService accountBookTransactionQueryService;

    @PostMapping
    public ResponseDto<AccountBookTransactionListResponseDto> getTransactions(
            @PathVariable Long accountBookId,
            @RequestBody @Valid AccountBookTransactionRequestDto request
    ) {
        return ResponseUtil.ok(accountBookTransactionService.getTransactions(accountBookId, request));
    }

    @GetMapping("/months")
    public ResponseDto<List<AccountBookTransactionMonthResponseDto>> getTransactionMonths(
            @PathVariable Long accountBookId
    ) {
        return ResponseUtil.ok(
                accountBookTransactionQueryService.getTransactionMonths(accountBookId)
        );
    }
}