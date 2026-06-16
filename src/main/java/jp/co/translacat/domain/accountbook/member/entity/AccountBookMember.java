package jp.co.translacat.domain.accountbook.member.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "account_book_member",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_account_book_member_account_book_user",
                        columnNames = {"account_book_id", "user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBookMember extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_book_id", nullable = false)
    private AccountBook accountBook;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountBookMemberRole role;

    @Column(nullable = false)
    private boolean deleted = false;

    private AccountBookMember(
            AccountBook accountBook,
            User user,
            AccountBookMemberRole role
    ) {
        this.accountBook = accountBook;
        this.user = user;
        this.role = role;
    }

    public static AccountBookMember createOwner(
            AccountBook accountBook,
            User user
    ) {
        return new AccountBookMember(
                accountBook,
                user,
                AccountBookMemberRole.OWNER
        );
    }

    public static AccountBookMember createMember(
            AccountBook accountBook,
            User user
    ) {
        return new AccountBookMember(
                accountBook,
                user,
                AccountBookMemberRole.MEMBER
        );
    }

    public static AccountBookMember createMember(
            AccountBook accountBook,
            User user,
            AccountBookMemberRole role
    ) {
        return new AccountBookMember(
                accountBook,
                user,
                role
        );
    }

    public boolean isOwner() {
        return this.role == AccountBookMemberRole.OWNER;
    }

    public void delete() {
        this.deleted = true;
    }

    public void restoreAsMember() {
        this.deleted = false;
        this.role = AccountBookMemberRole.MEMBER;
    }

    public void restoreAsRole(AccountBookMemberRole role) {
        this.deleted = false;
        this.role = role;
    }
}