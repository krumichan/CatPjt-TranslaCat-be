package jp.co.translacat.domain.support.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.support.enums.SupportNotificationType;
import jp.co.translacat.domain.support.enums.SupportTargetType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.Base;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "support_notification", indexes = {
        @Index(name = "idx_support_notification_user_read", columnList = "recipient_id,read_at,id"),
        @Index(name = "idx_support_notification_target", columnList = "target_type,target_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportNotification extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupportNotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column
    private Long replyId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column
    private LocalDateTime readAt;

    public static SupportNotification create(
            User recipient, SupportNotificationType type, SupportTargetType targetType,
            Long targetId, Long replyId, String title
    ) {
        SupportNotification notification = new SupportNotification();
        notification.recipient = recipient;
        notification.type = type;
        notification.targetType = targetType;
        notification.targetId = targetId;
        notification.replyId = replyId;
        notification.title = title;
        return notification;
    }
}
