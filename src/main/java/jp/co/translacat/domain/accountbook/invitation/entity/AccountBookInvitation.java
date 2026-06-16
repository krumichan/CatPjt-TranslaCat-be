package jp.co.translacat.domain.accountbook.invitation.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.invitation.enums.AccountBookInvitationStatus;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "account_book_invitation",
        indexes = {
                @Index(
                        name = "idx_account_book_invitation_invitee_status",
                        columnList = "invitee_user_id,status"
                ),
                @Index(
                        name = "idx_account_book_invitation_account_book_status",
                        columnList = "account_book_id,status"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBookInvitation extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_book_id", nullable = false)
    private AccountBook accountBook;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_user_id", nullable = false)
    private User inviter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitee_user_id", nullable = false)
    private User invitee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountBookMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountBookInvitationStatus status;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    private AccountBookInvitation(
            AccountBook accountBook,
            User inviter,
            User invitee,
            AccountBookMemberRole role
    ) {
        this.accountBook = accountBook;
        this.inviter = inviter;
        this.invitee = invitee;
        this.role = role;
        this.status = AccountBookInvitationStatus.PENDING;
    }

    public static AccountBookInvitation createPending(
            AccountBook accountBook,
            User inviter,
            User invitee,
            AccountBookMemberRole role
    ) {
        return new AccountBookInvitation(
                accountBook,
                inviter,
                invitee,
                role
        );
    }

    public boolean isPending() {
        return this.status == AccountBookInvitationStatus.PENDING;
    }

    public void accept() {
        if (!isPending()) {
            throw new IllegalStateException("대기 중인 초대만 수락할 수 있습니다.");
        }

        this.status = AccountBookInvitationStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void reject() {
        if (!isPending()) {
            throw new IllegalStateException("대기 중인 초대만 거절할 수 있습니다.");
        }

        this.status = AccountBookInvitationStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (!isPending()) {
            throw new IllegalStateException("대기 중인 초대만 취소할 수 있습니다.");
        }

        this.status = AccountBookInvitationStatus.CANCELED;
        this.respondedAt = LocalDateTime.now();
    }
}