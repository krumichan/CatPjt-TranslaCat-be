package jp.co.translacat.domain.chat.message.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.enums.ChatMessageType;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "chat_message",
        indexes = {
                @Index(
                        name = "idx_chat_message_room_created_id",
                        columnList = "chat_room_id, created_at, id"
                ),
                @Index(
                        name = "idx_chat_message_room_id_id",
                        columnList = "chat_room_id, id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private User senderUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatMessageSenderType senderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatMessageType messageType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatMessageStatus status;

    @Column
    private LocalDateTime deletedAt;

    private ChatMessage(
            ChatRoom chatRoom,
            User senderUser,
            ChatMessageSenderType senderType,
            ChatMessageType messageType,
            String content
    ) {
        this.chatRoom = chatRoom;
        this.senderUser = senderUser;
        this.senderType = senderType;
        this.messageType = messageType;
        this.content = content;
        this.status = ChatMessageStatus.SENT;
    }

    public static ChatMessage createUserTextMessage(
            ChatRoom chatRoom,
            User senderUser,
            String content
    ) {
        return new ChatMessage(
                chatRoom,
                senderUser,
                ChatMessageSenderType.USER,
                ChatMessageType.TEXT,
                content
        );
    }

    public static ChatMessage createSystemMessage(
            ChatRoom chatRoom,
            String content
    ) {
        return new ChatMessage(
                chatRoom,
                null,
                ChatMessageSenderType.SYSTEM,
                ChatMessageType.SYSTEM,
                content
        );
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void softDelete() {
        this.status = ChatMessageStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null || this.status == ChatMessageStatus.DELETED;
    }

    public boolean isSent() {
        return this.status == ChatMessageStatus.SENT;
    }

    public boolean isUserMessage() {
        return this.senderType == ChatMessageSenderType.USER;
    }

    public boolean isSystemMessage() {
        return this.messageType == ChatMessageType.SYSTEM;
    }
}