package jp.co.translacat.domain.accountbook.transaction.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookStoreSuggestionResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionListResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionMonthResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionRequestDto;
import jp.co.translacat.domain.accountbook.transaction.query.AccountBookTransactionQueryService;
import jp.co.translacat.domain.accountbook.transaction.service.AccountBookTransactionService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @RequestBody @Valid AccountBookTransactionRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookTransactionService.getTransactions(
                        accountBookId,
                        request,
                        userPrincipal.getId()
                )
        );
    }

    @GetMapping("/months")
    public ResponseDto<List<AccountBookTransactionMonthResponseDto>> getTransactionMonths(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId
    ) {
        return ResponseUtil.ok(
                accountBookTransactionQueryService.getTransactionMonths(
                        accountBookId,
                        userPrincipal.getId()
                )
        );
    }

    @GetMapping("/stores/suggestions")
    public ResponseDto<List<AccountBookStoreSuggestionResponseDto>> getStoreSuggestions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @RequestParam(required = false) String keyword
    ) {
        return ResponseUtil.ok(
                accountBookTransactionQueryService.getStoreSuggestions(
                        accountBookId,
                        keyword,
                        userPrincipal.getId()
                )
        );
    }

    @DeleteMapping("/{transactionId}")
    public ResponseDto<Boolean> deleteTransaction(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountBookId,
            @PathVariable Long transactionId
    ) {
        return ResponseUtil.ok(
                accountBookTransactionService.deleteTransaction(
                        accountBookId,
                        transactionId,
                        userPrincipal.getId()
                )
        );
    }
}