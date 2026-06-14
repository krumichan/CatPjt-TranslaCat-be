package jp.co.translacat.domain.accountbook.transaction.controller;

import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptAnalysisResponseDto;
import jp.co.translacat.domain.accountbook.transaction.facade.AccountBookReceiptAnalysisFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account-books/{accountBookId}/transactions")
public class AccountBookReceiptAnalysisController {

    private final AccountBookReceiptAnalysisFacade accountBookReceiptAnalysisFacade;

    @PostMapping(
            value = "/receipt-analysis",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseDto<ReceiptAnalysisResponseDto> analyzeReceipt(
            @PathVariable Long accountBookId,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestPart("file") MultipartFile file
    ) {
        ReceiptAnalysisResponseDto response = accountBookReceiptAnalysisFacade.analyze(
                accountBookId,
                userPrincipal.getId(),
                file
        );

        return ResponseUtil.ok(response);
    }
}