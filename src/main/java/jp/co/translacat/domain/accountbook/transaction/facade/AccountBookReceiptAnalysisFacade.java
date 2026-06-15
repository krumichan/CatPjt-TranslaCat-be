package jp.co.translacat.domain.accountbook.transaction.facade;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.receiptkeyword.service.ReceiptAnalysisOptionQueryService;
import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptAnalysisResponseDto;
import jp.co.translacat.domain.accountbook.transaction.enums.ReceiptAnalysisMode;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisOptions;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class AccountBookReceiptAnalysisFacade {

    private final AccountBookAccessService accountBookAccessService;
    private final ReceiptAnalysisOptionQueryService receiptAnalysisOptionQueryService;
    private final AiServerClient aiServerClient;

    public ReceiptAnalysisResponseDto analyze(
            Long accountBookId,
            Long userId,
            MultipartFile file,
            String analysisMode
    ) {
        AccountBook accountBook = accountBookAccessService.getAccessibleAccountBook(
                accountBookId,
                userId
        );

        String currencyCode = resolveCurrencyCode(accountBook);

        AiReceiptAnalysisOptions options = receiptAnalysisOptionQueryService
                .getOptions(currencyCode)
                .withAnalysisMode(
                        ReceiptAnalysisMode.fromNullable(analysisMode).name()
                );

        AiReceiptAnalysisResponse aiResponse = aiServerClient.callReceiptAnalysis(
                file,
                options
        );

        return toResponse(aiResponse);
    }

    private ReceiptAnalysisResponseDto toResponse(AiReceiptAnalysisResponse response) {
        return new ReceiptAnalysisResponseDto(
                response.title(),
                response.storeName(),
                response.amount(),
                response.transactionDate(),
                response.categoryName(),
                response.memo(),
                response.confidence(),
                response.rawText(),
                response.ocrEngine(),
                response.usedAi()
        );
    }

    private String resolveCurrencyCode(AccountBook accountBook) {
        return accountBook.getCurrency().getCode();
    }
}