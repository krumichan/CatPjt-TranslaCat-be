package jp.co.translacat.domain.chat.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jp.co.translacat.domain.chat.notification.enums.ChatNotificationType;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.Base;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "chat_notification",
        indexes = {
                @Index(
                        name = "idx_chat_notification_recipient_read_id",
                        columnList = "recipient_user_id, is_read, id"
                ),
                @Index(
                        name = "idx_chat_notification_recipient_created",
                        columnList = "recipient_user_id, created_at"
                ),
                @Index(
                        name = "idx_chat_notification_room_id",
                        columnList = "chat_room_id"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_notification_recipient_type_source",
                        columnNames = {
                                "recipient_user_id",
                                "notification_type",
                                "source_event_key"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatNotification extends Base {

    private static final String EMPTY_PAYLOAD_JSON = "{}";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private ChatNotificationType notificationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson = EMPTY_PAYLOAD_JSON;

    @Column(name = "source_event_key", nullable = false, length = 160)
    private String sourceEventKey;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private ChatNotification(
            User recipientUser,
            ChatNotificationType notificationType,
            ChatRoom chatRoom,
            User actorUser,
            String payloadJson,
            String sourceEventKey
    ) {
        this.recipientUser = recipientUser;
        this.notificationType = notificationType;
        this.chatRoom = chatRoom;
        this.actorUser = actorUser;
        this.payloadJson = normalizePayload(payloadJson);
        this.sourceEventKey = sourceEventKey.trim();
    }

    public static ChatNotification create(
            User recipientUser,
            ChatNotificationType notificationType,
            ChatRoom chatRoom,
            User actorUser,
            String payloadJson,
            String sourceEventKey
    ) {
        if (recipientUser == null
                || notificationType == null
                || sourceEventKey == null
                || sourceEventKey.isBlank()) {
            throw new IllegalArgumentException(
                    "채팅 활동 알림 생성 값이 올바르지 않습니다."
            );
        }
        if (sourceEventKey.trim().length() > 160) {
            throw new IllegalArgumentException(
                    "채팅 활동 알림 sourceEventKey가 너무 깁니다."
            );
        }
        return new ChatNotification(
                recipientUser,
                notificationType,
                chatRoom,
                actorUser,
                payloadJson,
                sourceEventKey
        );
    }

    public boolean markRead(LocalDateTime readAt) {
        if (this.read) {
            return false;
        }
        if (readAt == null) {
            throw new IllegalArgumentException(
                    "알림 읽음 시각은 필수입니다."
            );
        }
        this.read = true;
        this.readAt = readAt;
        return true;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static String normalizePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return EMPTY_PAYLOAD_JSON;
        }
        return payloadJson.trim();
    }
}
