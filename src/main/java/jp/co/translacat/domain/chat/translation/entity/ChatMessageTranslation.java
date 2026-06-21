package jp.co.translacat.domain.chat.translation.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.translation.enums.ChatMessageTranslationStatus;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "chat_message_translation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_message_translation_message_language",
                        columnNames = {"chat_message_id", "language_code"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_chat_translation_message_id",
                        columnList = "chat_message_id"
                ),
                @Index(
                        name = "idx_chat_translation_status",
                        columnList = "status"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageTranslation extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;

    @Column(nullable = false, length = 10)
    private String languageCode;

    @Column(columnDefinition = "TEXT")
    private String translatedContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatMessageTranslationStatus status;

    @Column(length = 1000)
    private String failureReason;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime deletedAt;

    private ChatMessageTranslation(
            ChatMessage chatMessage,
            String languageCode
    ) {
        this.chatMessage = chatMessage;
        this.languageCode = languageCode;
        this.status = ChatMessageTranslationStatus.PENDING;
    }

    public static ChatMessageTranslation createPending(
            ChatMessage chatMessage,
            String languageCode
    ) {
        return new ChatMessageTranslation(
                chatMessage,
                languageCode
        );
    }

    public void complete(String translatedContent) {
        this.translatedContent = translatedContent;
        this.status = ChatMessageTranslationStatus.COMPLETED;
        this.failureReason = null;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String failureReason) {
        this.status = ChatMessageTranslationStatus.FAILED;
        this.failureReason = failureReason;
        this.completedAt = null;
    }

    public void retry() {
        this.status = ChatMessageTranslationStatus.PENDING;
        this.failureReason = null;
        this.completedAt = null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return this.status == ChatMessageTranslationStatus.PENDING;
    }

    public boolean isCompleted() {
        return this.status == ChatMessageTranslationStatus.COMPLETED;
    }

    public boolean isFailed() {
        return this.status == ChatMessageTranslationStatus.FAILED;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}