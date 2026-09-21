package jp.co.translacat.domain.accountbook.transaction.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.currency.service.MoneyAmount;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionSourceType;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.global.jpa.BaseAuditable;
import jp.co.translacat.global.utils.DomainStringUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.text.Normalizer;
import java.util.Locale;

@Getter
@Entity
@Table(
        name = "account_book_transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_account_book_transaction_source",
                        columnNames = {
                                "account_book_id",
                                "source_type",
                                "source_id",
                                "source_year",
                                "source_month"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBookTransaction extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 어느 가계부의 거래인지
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_book_id", nullable = false)
    private AccountBook accountBook;

    /**
     * INCOME / EXPENSE
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountBookTransactionType type;

    /**
     * 금액
     */
    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal amount;

    @Column(precision = 28, scale = 8)
    private BigDecimal originalAmount;
    @Column(length = 3)
    private String originalCurrencyCode;
    @Column(precision = 38, scale = 18)
    private BigDecimal exchangeRate;
    private LocalDate requestedRateDate;
    private LocalDate effectiveRateDate;
    @Column(length = 50)
    private String exchangeRateProvider;
    @Column(length = 3)
    private String targetCurrencyCode;
    private Instant rateFetchedAt;
    @Column(nullable = true)
    private Instant convertedAt;
    private Integer roundingPrecision;
    @Column(length = 20)
    private String roundingMode;
    @Column(length = 40)
    private String conversionPolicyVersion;
    @Column(length = 64)
    private String conversionQuoteId;

    @Column(precision = 28, scale = 8)
    private BigDecimal purchaseTotal;
    @Column(precision = 28, scale = 8)
    private BigDecimal bookAmount;
    @Column(columnDefinition = "TEXT")
    private String receiptPaymentBreakdownJson;
    @Column(precision = 28, scale = 8)
    private BigDecimal cashTendered;
    @Column(precision = 28, scale = 8)
    private BigDecimal changeAmount;
    @Column(length = 40)
    private String amountPolicyVersion;
    @Column(length = 100)
    private String amountReason;
    @Column(length = 20)
    private String amountReviewStatus;
    @Column(length = 100)
    private String receiptBranchName;
    @Column(length = 120)
    private String merchantKey;
    @Column(length = 100)
    private String receiptSourceImageId;
    private Integer receiptAnalysisRevision;
    @Column(length = 8)
    private String receiptTransactionTime;

    public void recordReceiptConversion(jp.co.translacat.domain.accountbook.transaction.dto.ReceiptConversionResponseDto conversion) {
        if (!conversion.registrable() || amount.compareTo(conversion.convertedAmount()) != 0) {
            throw new IllegalArgumentException("Receipt conversion does not match transaction amount.");
        }
        this.originalAmount = conversion.originalAmount();
        this.originalCurrencyCode = conversion.originalCurrencyCode();
        this.exchangeRate = conversion.exchangeRate();
        this.requestedRateDate = conversion.requestedRateDate();
        this.effectiveRateDate = conversion.effectiveRateDate();
        this.exchangeRateProvider = conversion.exchangeRateProvider();
        this.targetCurrencyCode = conversion.accountBookCurrencyCode();
        this.rateFetchedAt = conversion.rateFetchedAt();
        this.convertedAt = conversion.convertedAt();
        this.roundingPrecision = conversion.roundingPrecision();
        this.roundingMode = conversion.roundingMode();
        this.conversionPolicyVersion = conversion.conversionPolicyVersion();
        this.conversionQuoteId = conversion.conversionQuoteId();
    }

    public void recordReceiptFacts(
            BigDecimal purchaseTotal,
            BigDecimal bookAmount,
            String paymentBreakdownJson,
            BigDecimal cashTendered,
            BigDecimal changeAmount,
            String amountPolicyVersion,
            String amountReason,
            String amountReviewStatus,
            String branchName,
            String sourceImageId,
            Integer analysisRevision,
            String transactionTime) {
        if (bookAmount == null || originalAmount == null
                || bookAmount.compareTo(originalAmount) != 0) {
            throw new IllegalArgumentException("Receipt amount facts do not match conversion.");
        }
        this.purchaseTotal = purchaseTotal;
        this.bookAmount = bookAmount;
        this.receiptPaymentBreakdownJson = paymentBreakdownJson;
        this.cashTendered = cashTendered;
        this.changeAmount = changeAmount;
        this.amountPolicyVersion = amountPolicyVersion;
        this.amountReason = amountReason;
        this.amountReviewStatus = amountReviewStatus;
        this.receiptBranchName = DomainStringUtil.normalizeNullable(branchName);
        this.receiptSourceImageId = DomainStringUtil.normalizeRequired(
                sourceImageId, "Receipt source image id is required.");
        this.receiptAnalysisRevision = analysisRevision;
        this.receiptTransactionTime = DomainStringUtil.normalizeNullable(transactionTime);
    }

    /**
     * 거래명
     * 예: 점심, 월급, 전기세
     */
    @Column(nullable = false, length = 100)
    private String title;

    /**
     * 점포명
     * 예: 松屋, Amazon, 세븐일레븐
     */
    @Column(length = 100)
    private String storeName;

    /**
     * 카테고리
     * 예: 식비, 교통비, 월급
     */
    @Column(nullable = false, length = 50)
    private String category;

    /**
     * 거래일
     */
    @Column(nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AccountBookTransactionSourceType sourceType;

    private Long sourceId;

    private Integer sourceYear;

    private Integer sourceMonth;

    /**
     * 메모
     */
    @Column(length = 500)
    private String memo;

    private AccountBookTransaction(
            AccountBook accountBook,
            AccountBookTransactionType type,
            BigDecimal amount,
            String title,
            String storeName,
            String category,
            LocalDate transactionDate,
            String memo
    ) {
        this.accountBook = accountBook;
        this.type = type;
        this.amount = MoneyAmount.positive(amount, accountBook.getCurrency());
        this.title = DomainStringUtil.normalizeRequired(title, "Title is required.");
        this.storeName = DomainStringUtil.normalizeNullable(storeName);
        this.merchantKey = merchantKey(this.storeName);
        this.category = DomainStringUtil.normalizeRequired(category, "Category is required.");
        this.transactionDate = transactionDate;
        this.memo = DomainStringUtil.normalizeNullable(memo);
    }

    public static AccountBookTransaction create(
            AccountBook accountBook,
            AccountBookTransactionType type,
            BigDecimal amount,
            String title,
            String storeName,
            String category,
            LocalDate transactionDate,
            String memo
    ) {
        return new AccountBookTransaction(
                accountBook,
                type,
                amount,
                title,
                storeName,
                category,
                transactionDate,
                memo
        );
    }

    public static AccountBookTransaction createFromFixedCost(
            AccountBook accountBook,
            String title,
            String storeName,
            String category,
            BigDecimal amount,
            LocalDate transactionDate,
            String memo,
            Long fixedCostId,
            Integer sourceYear,
            Integer sourceMonth
    ) {
        AccountBookTransaction transaction = new AccountBookTransaction();

        transaction.accountBook = accountBook;
        transaction.type = AccountBookTransactionType.EXPENSE;
        transaction.title = DomainStringUtil.normalizeRequired(title, "Title is required.");
        transaction.storeName = DomainStringUtil.normalizeNullable(storeName);
        transaction.merchantKey = merchantKey(transaction.storeName);
        transaction.category = DomainStringUtil.normalizeRequired(category, "Category is required.");
        transaction.amount = MoneyAmount.positive(amount, accountBook.getCurrency());
        transaction.transactionDate = transactionDate;
        transaction.memo = DomainStringUtil.normalizeNullable(memo);
        transaction.sourceType = AccountBookTransactionSourceType.FIXED_COST;
        transaction.sourceId = fixedCostId;
        transaction.sourceYear = sourceYear;
        transaction.sourceMonth = sourceMonth;

        return transaction;
    }

    public void update(
            AccountBookTransactionType type,
            BigDecimal amount,
            String title,
            String storeName,
            String category,
            LocalDate transactionDate,
            String memo
    ) {
        if (originalAmount != null && (this.type != type || this.amount.compareTo(amount) != 0 || !this.transactionDate.equals(transactionDate))) {
            throw new IllegalArgumentException("A receipt transaction's converted amount and date cannot be changed through manual editing.");
        }
        this.type = type;
        // Metadata edits preserve the recorded conversion even if an admin changes display precision.
        if (originalAmount == null) this.amount = MoneyAmount.positive(amount, accountBook.getCurrency());
        this.title = DomainStringUtil.normalizeRequired(title, "Title is required.");
        this.storeName = DomainStringUtil.normalizeNullable(storeName);
        this.merchantKey = merchantKey(this.storeName);
        this.category = DomainStringUtil.normalizeRequired(category, "Category is required.");
        this.transactionDate = transactionDate;
        this.memo = DomainStringUtil.normalizeNullable(memo);
    }

    private static String merchantKey(String storeName) {
        if (storeName == null) return null;
        String normalized = Normalizer.normalize(storeName, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Pd}]+", " ")
                .trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
