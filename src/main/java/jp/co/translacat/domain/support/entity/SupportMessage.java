package jp.co.translacat.domain.support.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.Base;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "support_message", indexes = {
        @Index(name = "idx_support_message_recipient_id", columnList = "recipient_id,id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportMessage extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column
    private LocalDateTime readAt;

    public static SupportMessage create(User sender, User recipient, String title, String content) {
        SupportMessage message = new SupportMessage();
        message.sender = sender;
        message.recipient = recipient;
        message.title = title;
        message.content = content;
        return message;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
