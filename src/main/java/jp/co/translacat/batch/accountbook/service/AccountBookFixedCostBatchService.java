package jp.co.translacat.batch.accountbook.service;

import jp.co.translacat.batch.accountbook.AccountBookFixedCostBatchResult;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostGenerateRequestDto;
import jp.co.translacat.domain.accountbook.fixedcost.dto.AccountBookFixedCostGenerateResponseDto;
import jp.co.translacat.domain.accountbook.fixedcost.repository.AccountBookFixedCostRepository;
import jp.co.translacat.domain.accountbook.fixedcost.service.AccountBookFixedCostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountBookFixedCostBatchService {

    private final AccountBookFixedCostRepository accountBookFixedCostRepository;
    private final AccountBookFixedCostService accountBookFixedCostService;

    public AccountBookFixedCostBatchResult generateTransactionsForMonth(
            YearMonth targetMonth
    ) {
        LocalDate targetMonthDate = targetMonth.atDay(1);

        List<Long> accountBookIds =
                accountBookFixedCostRepository.findGenerationTargetAccountBookIds(
                        targetMonthDate
                );

        int successAccountBookCount = 0;
        int failedAccountBookCount = 0;
        int generatedTransactionCount = 0;

        for (Long accountBookId : accountBookIds) {
            try {
                AccountBookFixedCostGenerateResponseDto response =
                        accountBookFixedCostService.generateTransactionsForBatch(
                                accountBookId,
                                new AccountBookFixedCostGenerateRequestDto(
                                        targetMonth.getYear(),
                                        targetMonth.getMonthValue()
                                )
                        );

                successAccountBookCount++;
                generatedTransactionCount += response.generatedCount();
            } catch (Exception e) {
                failedAccountBookCount++;

                log.error(
                        "Failed to generate fixed cost transactions. accountBookId={}, targetMonth={}",
                        accountBookId,
                        targetMonth,
                        e
                );
            }
        }

        return new AccountBookFixedCostBatchResult(
                targetMonth,
                accountBookIds.size(),
                successAccountBookCount,
                failedAccountBookCount,
                generatedTransactionCount
        );
    }
}
