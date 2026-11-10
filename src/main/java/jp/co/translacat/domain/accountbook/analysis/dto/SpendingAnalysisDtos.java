package jp.co.translacat.domain.accountbook.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    public enum PeriodType { MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY, CUSTOM }

    public record Request(
            @NotNull PeriodType periodType,
            @NotNull @Min(2000) @Max(9998) Integer year,
            @Min(1) @Max(12) Integer month,
            @NotNull @Pattern(regexp = "ko|ja|en") String locale,
            @Size(max = 2000) String comment,
            @Min(1) @Max(4) Integer quarter,
            @Min(1) @Max(2) Integer half,
            LocalDate startDate,
            LocalDate endDate
    ) {
        public Request(PeriodType periodType, Integer year, Integer month, String locale, String comment) {
            this(periodType, year, month, locale, comment, null, null, null, null);
        }

        @JsonIgnore
        @AssertTrue(message = "The selected period is invalid or shorter than one calendar month.")
        public boolean isValidPeriod() {
            try {
                jp.co.translacat.domain.accountbook.analysis.service.SpendingAnalysisPeriods.resolve(this);
                return true;
            } catch (IllegalArgumentException error) {
                return false;
            }
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
                            List<AiTransaction> transactions, Integer quarter, Integer half,
                            List<AiTransaction> historicalTransactions, List<String> previousAdvice) {
        public AiRequest(PeriodType periodType, int year, Integer month, String locale, String currencyCode,
                         int decimalPlaces, LocalDate periodStart, LocalDate periodEndExclusive, String totalExpense,
                         long transactionCount, Coverage coverage, PreviousPeriod previousPeriod, String comment,
                         List<AiTransaction> transactions) {
            this(periodType, year, month, locale, currencyCode, decimalPlaces, periodStart, periodEndExclusive,
                    totalExpense, transactionCount, coverage, previousPeriod, comment, transactions, null, null, List.of(), List.of());
        }
    }
    public record SavingsScenario(String lower, String estimate, String upper, String grossReduction,
                                  String replacementCost, int reductionLowBps, int reductionHighBps,
                                  int replacementLowBps, int replacementHighBps, String kind, String assumption, String basisAmount) {
        public SavingsScenario(String lower, String estimate, String upper, String grossReduction, String replacementCost,
                               int reductionLowBps, int reductionHighBps, int replacementLowBps, int replacementHighBps, String kind, String assumption) {
            this(lower, estimate, upper, grossReduction, replacementCost, reductionLowBps, reductionHighBps,
                    replacementLowBps, replacementHighBps, kind, assumption, null);
        }
    }
    public record AiFinding(String title, String reason, String action, String contextQuestion,
                             String uncertainty, List<String> evidenceTransactionRefs, String opinion,
                             List<String> historicalTransactionRefs, SavingsScenario savings, List<String> savingsTransactionRefs) {
        public AiFinding(String title, String reason, String action, String contextQuestion, String uncertainty,
                         List<String> evidenceTransactionRefs) {
            this(title, reason, action, contextQuestion, uncertainty, evidenceTransactionRefs, null, List.of(), null, List.of());
        }
    }
    public record AiResponse(String summary, List<AiFinding> findings, String policyVersion, SavingsScenario savings) {
        public AiResponse(String summary, List<AiFinding> findings) { this(summary, findings, "legacy-v2", null); }
    }
    public record Finding(String id, String title, String reason, String action, String contextQuestion,
                          String uncertainty, String evidenceAmount,
                          List<AccountBookTransactionResponseDto> evidenceTransactions, String opinion,
                          List<AccountBookTransactionResponseDto> historicalTransactions, SavingsScenario savings,
                          List<AccountBookTransactionResponseDto> savingsTransactions) {
        public Finding(String id, String title, String reason, String action, String contextQuestion, String uncertainty,
                       String evidenceAmount, List<AccountBookTransactionResponseDto> evidenceTransactions) {
            this(id, title, reason, action, contextQuestion, uncertainty, evidenceAmount, evidenceTransactions, null, List.of(), null, List.of());
        }
    }
    public record Response(PeriodType periodType, int year, Integer month, LocalDate periodStart,
                           LocalDate periodEndExclusive, String currencyCode, int decimalPlaces,
                           String totalExpense, long transactionCount, Coverage coverage, String summary,
                           List<Finding> findings, String contextComment, Instant generatedAt,
                           String snapshotFingerprint, String analysisId, Integer quarter, Integer half,
                           String policyVersion, SavingsScenario savings, Instant snapshotAt) {
        public Response(PeriodType periodType, int year, Integer month, LocalDate periodStart,
                        LocalDate periodEndExclusive, String currencyCode, int decimalPlaces,
                        String totalExpense, long transactionCount, Coverage coverage, String summary,
                        List<Finding> findings, String contextComment, Instant generatedAt, String snapshotFingerprint,
                        String analysisId, Integer quarter, Integer half, String policyVersion, SavingsScenario savings) {
            this(periodType, year, month, periodStart, periodEndExclusive, currencyCode, decimalPlaces, totalExpense,
                    transactionCount, coverage, summary, findings, contextComment, generatedAt, snapshotFingerprint,
                    analysisId, quarter, half, policyVersion, savings, null);
        }
        public Response(PeriodType periodType, int year, Integer month, LocalDate periodStart, LocalDate periodEndExclusive,
                        String currencyCode, int decimalPlaces, String totalExpense, long transactionCount, Coverage coverage,
                        String summary, List<Finding> findings, String contextComment, Instant generatedAt, String snapshotFingerprint) {
            this(periodType, year, month, periodStart, periodEndExclusive, currencyCode, decimalPlaces, totalExpense,
                    transactionCount, coverage, summary, findings, contextComment, generatedAt, snapshotFingerprint,
                    null, null, null, "legacy-v2", null);
        }
    }
    public record HistoryItem(String analysisId, PeriodType periodType, LocalDate periodStart, LocalDate periodEndExclusive,
                              String currencyCode, String totalExpense, String summary, String contextComment,
                              Instant generatedAt, String policyVersion, SavingsScenario savings) { }
    public record HistoryPage(List<HistoryItem> items, long total, int page, int size) { }
}
