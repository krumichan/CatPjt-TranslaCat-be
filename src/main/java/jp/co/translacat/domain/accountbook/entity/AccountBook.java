package jp.co.translacat.domain.accountbook.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Getter
@Table(name = "account_book")
public class AccountBook extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Integer capacity = 100;

    @Column(precision = 19, scale = 2)
    private BigDecimal expenseGoalAmount;

    @Column
    private boolean deleted = false;

    private AccountBook(
            User user,
            String name,
            String category,
            Currency currency,
            BigDecimal expenseGoalAmount
    ) {
        this.user = user;
        this.name = name;
        this.category = category;
        this.currency = currency;
        this.expenseGoalAmount = expenseGoalAmount;
    }

    public static AccountBook create(
            User user,
            Currency currency,
            String name,
            String category,
            BigDecimal expenseGoalAmount
    ) {
        return new AccountBook(user, name, category, currency, expenseGoalAmount);
    }
}
