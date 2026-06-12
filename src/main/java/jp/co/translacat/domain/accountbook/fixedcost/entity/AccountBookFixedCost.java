package jp.co.translacat.domain.accountbook.fixedcost.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.global.utils.DomainStringUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "account_book_fixed_cost")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBookFixedCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_book_id", nullable = false)
    private AccountBook accountBook;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 100)
    private String storeName;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer paymentDay;

    @Column(nullable = false)
    private LocalDate startMonth;

    private LocalDate endMonth;

    private LocalDate lastGeneratedMonth;

    @Column(length = 500)
    private String memo;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false)
    private Boolean deleted;

    private LocalDateTime deletedAt;

    private AccountBookFixedCost(
            AccountBook accountBook,
            String title,
            String storeName,
            String category,
            BigDecimal amount,
            Integer paymentDay,
            LocalDate startMonth,
            LocalDate endMonth,
            String memo
    ) {
        this.accountBook = accountBook;
        this.title = DomainStringUtil.normalizeRequired(title, "Title is required.");
        this.storeName = DomainStringUtil.normalizeNullable(storeName);
        this.category = DomainStringUtil.normalizeRequired(category, "Category is required.");
        this.amount = amount;
        this.paymentDay = paymentDay;
        this.startMonth = startMonth;
        this.endMonth = endMonth;
        this.memo = DomainStringUtil.normalizeNullable(memo);
        this.active = true;
        this.deleted = false;
    }

    public static AccountBookFixedCost create(
            AccountBook accountBook,
            String title,
            String storeName,
            String category,
            BigDecimal amount,
            Integer paymentDay,
            LocalDate startMonth,
            LocalDate endMonth,
            String memo
    ) {
        return new AccountBookFixedCost(
                accountBook,
                title,
                storeName,
                category,
                amount,
                paymentDay,
                startMonth,
                endMonth,
                memo
        );
    }

    public void update(
            String title,
            String storeName,
            String category,
            BigDecimal amount,
            Integer paymentDay,
            LocalDate startMonth,
            LocalDate endMonth,
            String memo
    ) {
        this.title = DomainStringUtil.normalizeRequired(title, "Title is required.");
        this.storeName = DomainStringUtil.normalizeNullable(storeName);
        this.category = DomainStringUtil.normalizeRequired(category, "Category is required.");
        this.amount = amount;
        this.paymentDay = paymentDay;
        this.startMonth = startMonth;
        this.endMonth = endMonth;
        this.memo = DomainStringUtil.normalizeNullable(memo);
    }

    public void delete() {
        this.deleted = true;
        this.active = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void updateLastGeneratedMonth(LocalDate lastGeneratedMonth) {
        this.lastGeneratedMonth = lastGeneratedMonth;
    }
}