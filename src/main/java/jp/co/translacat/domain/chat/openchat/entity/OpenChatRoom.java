package jp.co.translacat.domain.chat.openchat.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatRoomStatus;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "open_chat_room",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_open_chat_room_chat_room",
                        columnNames = "chat_room_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_open_chat_room_visibility_status",
                        columnList = "visibility, status"
                ),
                @Index(
                        name = "idx_open_chat_room_status",
                        columnList = "status"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpenChatRoom extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OpenChatVisibility visibility;

    @Column(name = "max_member_count", nullable = false)
    private int maxMemberCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OpenChatRoomStatus status;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    private OpenChatRoom(
            ChatRoom chatRoom,
            OpenChatVisibility visibility,
            int maxMemberCount
    ) {
        if (chatRoom == null
                || chatRoom.getRoomType() != ChatRoomType.OPEN) {
            throw new IllegalArgumentException(
                    "OPEN 타입 채팅방만 OPEN 확장 정보를 생성할 수 있습니다."
            );
        }
        this.chatRoom = chatRoom;
        this.visibility = visibility;
        this.maxMemberCount = maxMemberCount;
        this.status = OpenChatRoomStatus.ACTIVE;
    }

    public static OpenChatRoom create(
            ChatRoom chatRoom,
            OpenChatVisibility visibility,
            int maxMemberCount
    ) {
        return new OpenChatRoom(
                chatRoom,
                visibility,
                maxMemberCount
        );
    }

    public void close() {
        if (this.status == OpenChatRoomStatus.CLOSED) {
            return;
        }
        this.status = OpenChatRoomStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    public boolean isActiveStatus() {
        return this.status == OpenChatRoomStatus.ACTIVE;
    }

    public boolean isClosed() {
        return this.status == OpenChatRoomStatus.CLOSED;
    }

    public boolean isPublic() {
        return this.visibility == OpenChatVisibility.PUBLIC;
    }
}
