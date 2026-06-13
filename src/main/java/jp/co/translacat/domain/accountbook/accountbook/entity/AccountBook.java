package jp.co.translacat.domain.accountbook.accountbook.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "account_book")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private AccountBook(
            User user,
            String name,
            String category,
            Currency currency
    ) {
        this.user = user;
        this.name = name;
        this.category = category;
        this.currency = currency;
    }

    public static AccountBook create(
            User user,
            Currency currency,
            String name,
            String category
    ) {
        return new AccountBook(user, name, category, currency);
    }

    public void update(
            String name,
            String description,
            String category
    ) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
