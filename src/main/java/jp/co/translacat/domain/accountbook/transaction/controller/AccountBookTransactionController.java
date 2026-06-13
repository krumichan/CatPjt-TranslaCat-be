package jp.co.translacat.domain.accountbook.transaction.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.transaction.dto.*;
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

    @GetMapping("/stores/suggestions")
    public ResponseDto<List<AccountBookStoreSuggestionResponseDto>> getStoreSuggestions(
            @PathVariable Long accountBookId,
            @RequestParam(required = false) String keyword
    ) {
        return ResponseUtil.ok(
                accountBookTransactionQueryService.getStoreSuggestions(
                        accountBookId,
                        keyword
                )
        );
    }

    @PostMapping("/register")
    public ResponseDto<AccountBookTransactionResponseDto> createTransaction(
            @PathVariable Long accountBookId,
            @RequestBody @Valid AccountBookTransactionCreateRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookTransactionService.createTransaction(accountBookId, request)
        );
    }

    @PutMapping("/{transactionId}")
    public ResponseDto<AccountBookTransactionResponseDto> updateTransaction(
            @PathVariable Long accountBookId,
            @PathVariable Long transactionId,
            @RequestBody @Valid AccountBookTransactionUpdateRequestDto request
    ) {
        return ResponseUtil.ok(
                accountBookTransactionService.updateTransaction(
                        accountBookId,
                        transactionId,
                        request
                )
        );
    }

    @DeleteMapping("/{transactionId}")
    public ResponseDto<Boolean> deleteTransaction(
            @PathVariable Long accountBookId,
            @PathVariable Long transactionId
    ) {
        return ResponseUtil.ok(
                accountBookTransactionService.deleteTransaction(
                        accountBookId,
                        transactionId
                )
        );
    }
}