package jp.co.translacat.batch.accountbook;

import java.time.YearMonth;

public record AccountBookFixedCostBatchResult(
        YearMonth targetMonth,
        int targetAccountBookCount,
        int successAccountBookCount,
        int failedAccountBookCount,
        int generatedTransactionCount
) {
}
