package jp.co.translacat.domain.support.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.Base;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "support_notice_dismissal",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_support_notice_dismissal_user_notice",
                columnNames = {"user_id", "notice_id"}
        ),
        indexes = @Index(name = "idx_support_notice_dismissal_notice", columnList = "notice_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportNoticeDismissal extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private SupportNotice notice;

    public static SupportNoticeDismissal create(User user, SupportNotice notice) {
        SupportNoticeDismissal dismissal = new SupportNoticeDismissal();
        dismissal.user = user;
        dismissal.notice = notice;
        return dismissal;
    }
}
