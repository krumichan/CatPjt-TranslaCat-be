package jp.co.translacat.batch.accountbook;

import jp.co.translacat.batch.accountbook.service.AccountBookFixedCostBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountBookFixedCostBatch {

    private final AccountBookFixedCostBatchService accountBookFixedCostBatchService;

    @Value("${translacat.batch.fixed-cost.enabled:true}")
    private boolean enabled;

    @Value("${translacat.batch.zone:Asia/Tokyo}")
    private String zone;

    @Scheduled(
            cron = "${translacat.batch.fixed-cost.current-month.cron:0 0 3 1 * *}",
            zone = "${translacat.batch.zone:Asia/Tokyo}"
    )
    public void generateCurrentMonthFixedCostTransactions() {
        if (!enabled) {
            log.info("Fixed cost batch is disabled.");
            return;
        }

        YearMonth targetMonth = YearMonth.now(ZoneId.of(zone));

        log.info(
                "Fixed cost batch started. targetMonth={}",
                targetMonth
        );

        AccountBookFixedCostBatchResult result =
                accountBookFixedCostBatchService.generateTransactionsForMonth(
                        targetMonth
                );

        log.info(
                "Fixed cost batch finished. targetMonth={}, targetAccountBookCount={}, successAccountBookCount={}, failedAccountBookCount={}, generatedTransactionCount={}",
                result.targetMonth(),
                result.targetAccountBookCount(),
                result.successAccountBookCount(),
                result.failedAccountBookCount(),
                result.generatedTransactionCount()
        );
    }
}
