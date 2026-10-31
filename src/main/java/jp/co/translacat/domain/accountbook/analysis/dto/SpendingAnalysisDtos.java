package jp.co.translacat.domain.accountbook.analysis.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SpendingAnalysisDtos {
    private SpendingAnalysisDtos() { }

    public enum PeriodType { MONTHLY, YEARLY }

    public record Request(
            @NotNull PeriodType periodType,
            @NotNull @Min(2000) @Max(9998) Integer year,
            @Min(1) @Max(12) Integer month,
            @NotNull @Pattern(regexp = "ko|ja|en") String locale,
            @Size(max = 2000) String comment
    ) {
        @AssertTrue(message = "MONTHLY requires month; YEARLY must omit month.")
        public boolean isValidPeriod() {
            return periodType == null || (periodType == PeriodType.MONTHLY ? month != null : month == null);
        }
    }

    public record Coverage(long analyzedTransactionCount, long totalTransactionCount,
                           boolean isPartial, String reason) { }
    public record SnapshotInfo(String snapshotFingerprint, long transactionCount, String totalExpense) { }
    public record PreviousPeriod(LocalDate periodStart, LocalDate periodEndExclusive, String totalExpense,
                                 long transactionCount, boolean isComparable) { }
    public record AiTransaction(String refId, LocalDate date, String title, String storeName,
                                String category, String memo, String amount, String sourceType) { }
    public record AiRequest(PeriodType periodType, int year, Integer month, String locale,
                            String currencyCode, int decimalPlaces, LocalDate periodStart,
                            LocalDate periodEndExclusive, String totalExpense, long transactionCount,
                            Coverage coverage, PreviousPeriod previousPeriod, String comment,
                            List<AiTransaction> transactions) { }
    public record AiFinding(String title, String reason, String action, String contextQuestion,
                             String uncertainty, List<String> evidenceTransactionRefs) { }
    public record AiResponse(String summary, List<AiFinding> findings) { }
    public record Finding(String id, String title, String reason, String action, String contextQuestion,
                          String uncertainty, String evidenceAmount,
                          List<AccountBookTransactionResponseDto> evidenceTransactions) { }
    public record Response(PeriodType periodType, int year, Integer month, LocalDate periodStart,
                           LocalDate periodEndExclusive, String currencyCode, int decimalPlaces,
                           String totalExpense, long transactionCount, Coverage coverage, String summary,
                           List<Finding> findings, String contextComment, Instant generatedAt,
                           String snapshotFingerprint) { }
}
