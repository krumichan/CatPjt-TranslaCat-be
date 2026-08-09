package jp.co.translacat.domain.chat.message.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
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
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_message_ai_request_id",
                        columnNames = "ai_request_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_chat_message_room_created_id",
                        columnList = "chat_room_id, created_at, id"
                ),
                @Index(
                        name = "idx_chat_message_room_id_id",
                        columnList = "chat_room_id, id"
                ),
                @Index(
                        name = "idx_chat_message_sender_ai_member_id",
                        columnList = "sender_ai_member_id"
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_ai_member_id")
    private ChatRoomAiMember senderAiMember;

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

    @Column(name = "ai_request_id", length = 100)
    private String aiRequestId;

    @Column
    private LocalDateTime deletedAt;

    private ChatMessage(
            ChatRoom chatRoom,
            User senderUser,
            ChatRoomAiMember senderAiMember,
            ChatMessageSenderType senderType,
            ChatMessageType messageType,
            String content,
            String aiRequestId
    ) {
        this.chatRoom = chatRoom;
        this.senderUser = senderUser;
        this.senderAiMember = senderAiMember;
        this.senderType = senderType;
        this.messageType = messageType;
        this.content = content;
        this.aiRequestId = aiRequestId;
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
                null,
                ChatMessageSenderType.USER,
                ChatMessageType.TEXT,
                content,
                null
        );
    }


    public static ChatMessage createAiTextMessage(
            ChatRoom chatRoom,
            ChatRoomAiMember senderAiMember,
            String content
    ) {
        return createAiTextMessage(
                chatRoom,
                senderAiMember,
                content,
                null
        );
    }

    public static ChatMessage createAiTextMessage(
            ChatRoom chatRoom,
            ChatRoomAiMember senderAiMember,
            String content,
            String aiRequestId
    ) {
        if (senderAiMember == null) {
            throw new IllegalArgumentException("AI 발신 멤버는 필수입니다.");
        }
        ChatRoom senderRoom = senderAiMember.getChatRoom();
        boolean sameRoom = senderRoom != null
                && chatRoom != null
                && (senderRoom == chatRoom
                || (senderRoom.getId() != null
                && senderRoom.getId().equals(chatRoom.getId())));
        if (!sameRoom) {
            throw new IllegalArgumentException("AI 발신 멤버가 메시지 채팅방에 속하지 않습니다.");
        }
        return new ChatMessage(
                chatRoom,
                null,
                senderAiMember,
                ChatMessageSenderType.AI,
                ChatMessageType.TEXT,
                content,
                aiRequestId
        );
    }

    public static ChatMessage createSystemMessage(
            ChatRoom chatRoom,
            String content
    ) {
        return new ChatMessage(
                chatRoom,
                null,
                null,
                ChatMessageSenderType.SYSTEM,
                ChatMessageType.SYSTEM,
                content,
                null
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

    public boolean isAiMessage() {
        return this.senderType == ChatMessageSenderType.AI;
    }

    public boolean isSystemMessage() {
        return this.messageType == ChatMessageType.SYSTEM;
    }
}