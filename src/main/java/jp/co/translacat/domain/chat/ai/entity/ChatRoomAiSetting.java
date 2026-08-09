package jp.co.translacat.domain.chat.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.enums.ChatAiMentionPermission;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "chat_room_ai_setting",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_room_ai_setting_room",
                        columnNames = "chat_room_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomAiSetting extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false, updatable = false)
    private ChatRoom chatRoom;

    @Enumerated(EnumType.STRING)
    @Column(name = "disclosure_type", nullable = false, length = 20)
    private ChatAiDisclosureType disclosureType;

    @Enumerated(EnumType.STRING)
    @Column(name = "mention_permission", nullable = false, length = 30)
    private ChatAiMentionPermission mentionPermission;

    @Column(name = "conversation_enabled", nullable = false)
    private boolean conversationEnabled;

    @Column(name = "revival_enabled", nullable = false)
    private boolean revivalEnabled;

    private ChatRoomAiSetting(ChatRoom chatRoom) {
        if (chatRoom == null) {
            throw new IllegalArgumentException("채팅방은 필수입니다.");
        }
        this.chatRoom = chatRoom;
        this.disclosureType = ChatAiDisclosureType.PUBLIC;
        this.mentionPermission = ChatAiMentionPermission.ALL_MEMBERS;
        this.conversationEnabled = true;
        this.revivalEnabled = true;
    }

    public static ChatRoomAiSetting createDefault(ChatRoom chatRoom) {
        return new ChatRoomAiSetting(chatRoom);
    }

    public void update(
            ChatAiDisclosureType disclosureType,
            ChatAiMentionPermission mentionPermission,
            Boolean conversationEnabled,
            Boolean revivalEnabled
    ) {
        if (disclosureType != null) {
            this.disclosureType = disclosureType;
        }
        if (mentionPermission != null) {
            this.mentionPermission = mentionPermission;
        }
        if (conversationEnabled != null) {
            this.conversationEnabled = conversationEnabled;
        }
        if (revivalEnabled != null) {
            this.revivalEnabled = revivalEnabled;
        }
    }
}
