package jp.co.translacat.domain.chat.room.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "chat_room",
        indexes = {
                @Index(name = "idx_chat_room_owner_id", columnList = "owner_id"),
                @Index(name = "idx_chat_room_room_type", columnList = "room_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatRoomType roomType;

    @Column(length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private LocalDateTime deletedAt;

    private ChatRoom(
            ChatRoomType roomType,
            String name,
            String description,
            User owner
    ) {
        this.roomType = roomType;
        this.name = name;
        this.description = description;
        this.owner = owner;
    }

    public static ChatRoom createDirectRoom(User owner) {
        return new ChatRoom(
                ChatRoomType.DIRECT,
                null,
                null,
                owner
        );
    }

    public static ChatRoom createGroupRoom(
            String name,
            String description,
            User owner
    ) {
        return new ChatRoom(
                ChatRoomType.GROUP,
                name,
                description,
                owner
        );
    }

    public void updateGroupRoomInfo(
            String name,
            String description
    ) {
        this.name = name;
        this.description = description;
    }

    public void softDelete() {
        this.active = false;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}