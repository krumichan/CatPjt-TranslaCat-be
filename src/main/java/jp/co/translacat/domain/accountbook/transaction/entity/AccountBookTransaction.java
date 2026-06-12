package jp.co.translacat.domain.accountbook.transaction.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionSourceType;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.global.jpa.BaseAuditable;
import jp.co.translacat.global.utils.DomainStringUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

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
        this.amount = amount;
        this.title = title;
        this.storeName = storeName;
        this.category = category;
        this.transactionDate = transactionDate;
        this.memo = memo;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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
        transaction.category = DomainStringUtil.normalizeRequired(category, "Category is required.");
        transaction.amount = amount;
        transaction.transactionDate = transactionDate;
        transaction.memo = DomainStringUtil.normalizeNullable(memo);
        transaction.sourceType = AccountBookTransactionSourceType.FIXED_COST;
        transaction.sourceId = fixedCostId;
        transaction.sourceYear = sourceYear;
        transaction.sourceMonth = sourceMonth;

        return transaction;
    }
}