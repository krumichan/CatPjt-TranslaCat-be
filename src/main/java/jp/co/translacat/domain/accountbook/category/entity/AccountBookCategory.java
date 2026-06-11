package jp.co.translacat.domain.accountbook.category.entity;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "account_book_category",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_account_book_category_account_book_id_name",
                        columnNames = {"account_book_id", "name"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBookCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_book_id", nullable = false)
    private AccountBook accountBook;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Boolean active;

    private AccountBookCategory(
            AccountBook accountBook,
            String name,
            Integer displayOrder
    ) {
        this.accountBook = accountBook;
        this.name = name;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    public static AccountBookCategory create(
            AccountBook accountBook,
            String name,
            Integer displayOrder
    ) {
        return new AccountBookCategory(
                accountBook,
                normalizeName(name),
                displayOrder
        );
    }

    public void updateName(String name) {
        this.name = normalizeName(name);
    }

    public void updateDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required.");
        }

        return name.trim();
    }
}