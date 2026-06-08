package jp.co.translacat.domain.accountbook.transaction.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "account_book_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBookTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 어느 가계부의 거래인지
     */
    @ManyToOne(fetch = FetchType.LAZY)
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

    /**
     * 메모
     */
    @Column(length = 500)
    private String memo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

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
}