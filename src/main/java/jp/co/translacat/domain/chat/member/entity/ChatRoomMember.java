package jp.co.translacat.domain.chat.member.entity;

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
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
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
        name = "chat_room_member",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_room_member_room_user",
                        columnNames = {"chat_room_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_chat_room_member_user_active",
                        columnList = "user_id, active"
                ),
                @Index(
                        name = "idx_chat_room_member_room_active",
                        columnList = "chat_room_id, active"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatRoomMemberRole role;

    @Column(length = 10)
    private String originalLanguageCode;

    @Column(length = 10)
    private String translationLanguageCode;

    @Column(nullable = false)
    private boolean showOriginal = true;

    @Column(nullable = false)
    private boolean showTranslation = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column
    private LocalDateTime leftAt;

    @Column
    private LocalDateTime deletedAt;

    private ChatRoomMember(
            ChatRoom chatRoom,
            User user,
            ChatRoomMemberRole role,
            String originalLanguageCode,
            String translationLanguageCode,
            boolean showOriginal,
            boolean showTranslation
    ) {
        this.chatRoom = chatRoom;
        this.user = user;
        this.role = role;
        this.originalLanguageCode = originalLanguageCode;
        this.translationLanguageCode = translationLanguageCode;
        this.showOriginal = showOriginal;
        this.showTranslation = showTranslation;
        this.joinedAt = LocalDateTime.now();
    }

    private ChatRoomMember(
            ChatRoom chatRoom,
            User user,
            ChatRoomMemberRole role,
            String originalLanguageCode,
            String translationLanguageCode
    ) {
        this(
                chatRoom,
                user,
                role,
                originalLanguageCode,
                translationLanguageCode,
                true,
                true
        );
    }

    public static ChatRoomMember createOwner(
            ChatRoom chatRoom,
            User user,
            String originalLanguageCode,
            String translationLanguageCode
    ) {
        return new ChatRoomMember(
                chatRoom,
                user,
                ChatRoomMemberRole.OWNER,
                originalLanguageCode,
                translationLanguageCode
        );
    }

    public static ChatRoomMember createOwner(
            ChatRoom chatRoom,
            User user,
            String originalLanguageCode,
            String translationLanguageCode,
            boolean showOriginal,
            boolean showTranslation
    ) {
        return new ChatRoomMember(
                chatRoom,
                user,
                ChatRoomMemberRole.OWNER,
                originalLanguageCode,
                translationLanguageCode,
                showOriginal,
                showTranslation
        );
    }

    public static ChatRoomMember createMember(
            ChatRoom chatRoom,
            User user,
            String originalLanguageCode,
            String translationLanguageCode
    ) {
        return new ChatRoomMember(
                chatRoom,
                user,
                ChatRoomMemberRole.MEMBER,
                originalLanguageCode,
                translationLanguageCode
        );
    }

    public static ChatRoomMember createMember(
            ChatRoom chatRoom,
            User user,
            String originalLanguageCode,
            String translationLanguageCode,
            boolean showOriginal,
            boolean showTranslation
    ) {
        return new ChatRoomMember(
                chatRoom,
                user,
                ChatRoomMemberRole.MEMBER,
                originalLanguageCode,
                translationLanguageCode,
                showOriginal,
                showTranslation
        );
    }

    public void updateLanguageSetting(
            String originalLanguageCode,
            String translationLanguageCode,
            boolean showOriginal,
            boolean showTranslation
    ) {
        this.originalLanguageCode = originalLanguageCode;
        this.translationLanguageCode = translationLanguageCode;
        this.showOriginal = showOriginal;
        this.showTranslation = showTranslation;
    }

    public void changeRole(ChatRoomMemberRole role) {
        this.role = role;
    }

    public void leave() {
        this.active = false;
        this.leftAt = LocalDateTime.now();
    }

    public void leaveOpenChat() {
        this.role = ChatRoomMemberRole.MEMBER;
        this.active = false;
        this.leftAt = LocalDateTime.now();
    }

    public void restore(
            ChatRoomMemberRole role,
            String originalLanguageCode,
            String translationLanguageCode
    ) {
        this.role = role;
        this.originalLanguageCode = originalLanguageCode;
        this.translationLanguageCode = translationLanguageCode;
        this.showOriginal = true;
        this.showTranslation = true;
        this.active = true;
        this.joinedAt = LocalDateTime.now();
        this.leftAt = null;
        this.deletedAt = null;
        resetReadCursor();
    }

    public void restore(
            ChatRoomMemberRole role,
            String originalLanguageCode,
            String translationLanguageCode,
            boolean showOriginal,
            boolean showTranslation
    ) {
        this.role = role;
        this.originalLanguageCode = originalLanguageCode;
        this.translationLanguageCode = translationLanguageCode;
        this.showOriginal = showOriginal;
        this.showTranslation = showTranslation;
        this.active = true;
        this.joinedAt = LocalDateTime.now();
        this.leftAt = null;
        this.deletedAt = null;
        resetReadCursor();
    }

    public void initializeReadCursor(Long messageId) {
        this.lastReadMessageId = messageId;
        this.lastReadAt = messageId != null ? LocalDateTime.now() : null;
    }

    public boolean advanceReadCursor(Long messageId) {
        if (messageId == null) {
            return false;
        }
        if (this.lastReadMessageId != null
                && messageId.compareTo(this.lastReadMessageId) <= 0) {
            return false;
        }
        this.lastReadMessageId = messageId;
        this.lastReadAt = LocalDateTime.now();
        return true;
    }

    public void resetReadCursor() {
        this.lastReadMessageId = null;
        this.lastReadAt = null;
    }

    public void resetLanguageSetting() {
        this.originalLanguageCode = null;
        this.translationLanguageCode = null;
        this.showOriginal = true;
        this.showTranslation = true;
    }

    public boolean hasRoomLanguageSetting() {
        return this.originalLanguageCode != null
                || this.translationLanguageCode != null;
    }

    public void softDelete() {
        this.active = false;
        this.leftAt = LocalDateTime.now();
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean isOwner() {
        return this.role == ChatRoomMemberRole.OWNER;
    }

    public boolean isAdmin() {
        return this.role == ChatRoomMemberRole.ADMIN;
    }

    public boolean isMember() {
        return this.role == ChatRoomMemberRole.MEMBER;
    }
}
