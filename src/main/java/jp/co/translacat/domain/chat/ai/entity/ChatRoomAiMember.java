package jp.co.translacat.domain.chat.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "chat_room_ai_member",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_room_ai_member_room_agent",
                        columnNames = {"chat_room_id", "ai_agent_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_chat_room_ai_member_room_active",
                        columnList = "chat_room_id, active"
                ),
                @Index(
                        name = "idx_chat_room_ai_member_agent_active",
                        columnList = "ai_agent_id, active"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomAiMember extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_agent_id", nullable = false)
    private ChatAiAgent aiAgent;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private ChatRoomAiMember(ChatRoom chatRoom, ChatAiAgent aiAgent) {
        if (chatRoom == null || aiAgent == null) {
            throw new IllegalArgumentException("채팅방과 AI Agent는 필수입니다.");
        }
        this.chatRoom = chatRoom;
        this.aiAgent = aiAgent;
        this.joinedAt = LocalDateTime.now();
    }

    public static ChatRoomAiMember create(
            ChatRoom chatRoom,
            ChatAiAgent aiAgent
    ) {
        return new ChatRoomAiMember(chatRoom, aiAgent);
    }

    public void softDelete() {
        this.active = false;
        this.leftAt = LocalDateTime.now();
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
