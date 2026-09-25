package jp.co.translacat.domain.accountbook.transaction.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptBatchRequestDto;
import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptConversionRequestDto;
import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptConversionResponseDto;
import jp.co.translacat.domain.accountbook.transaction.service.ReceiptBatchService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account-books/{accountBookId}/transactions")
public class ReceiptBatchController {
    private final ReceiptBatchService receipts;

    @PostMapping("/receipt-batch")
    public ResponseDto<List<AccountBookTransactionResponseDto>> register(
            @PathVariable Long accountBookId,
            @AuthenticationPrincipal UserPrincipal user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid ReceiptBatchRequestDto request) {
        return ResponseUtil.ok(
                receipts.register(accountBookId, user.getId(), idempotencyKey, request));
    }

    @PostMapping("/receipt-conversion")
    public ResponseDto<ReceiptConversionResponseDto> convert(
            @PathVariable Long accountBookId,
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody @Valid ReceiptConversionRequestDto request) {
        return ResponseUtil.ok(receipts.recalculate(accountBookId, user.getId(), request));
    }
}
