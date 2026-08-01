package jp.co.translacat.domain.chat.openchat.ban.entity;

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
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
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
        name = "open_chat_ban",
        indexes = {
                @Index(
                        name = "idx_open_chat_ban_room_user_active",
                        columnList = "chat_room_id, target_user_id, released_at"
                ),
                @Index(
                        name = "idx_open_chat_ban_room_active_id",
                        columnList = "chat_room_id, released_at, id"
                ),
                @Index(
                        name = "idx_open_chat_ban_member_code",
                        columnList = "target_member_code"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpenChatBan extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_chat_room_member_id", nullable = false)
    private ChatRoomMember targetChatRoomMember;

    @Column(name = "target_member_code", nullable = false, length = 20)
    private String targetMemberCode;

    @Column(name = "nickname_snapshot", nullable = false, length = 50)
    private String nicknameSnapshot;

    @Column(name = "profile_image_object_key_snapshot", length = 500)
    private String profileImageObjectKeySnapshot;

    @Column(name = "last_joined_at_snapshot", nullable = false)
    private LocalDateTime lastJoinedAtSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role_snapshot", nullable = false, length = 30)
    private ChatRoomMemberRole targetRoleSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "banned_by_member_id", nullable = false)
    private ChatRoomMember bannedByMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "banned_by_role", nullable = false, length = 30)
    private ChatRoomMemberRole bannedByRole;

    @Column(name = "banned_at", nullable = false)
    private LocalDateTime bannedAt;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "released_by_member_id")
    private ChatRoomMember releasedByMember;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    private OpenChatBan(
            ChatRoom chatRoom,
            User targetUser,
            ChatRoomMember targetChatRoomMember,
            String targetMemberCode,
            String nicknameSnapshot,
            String profileImageObjectKeySnapshot,
            LocalDateTime lastJoinedAtSnapshot,
            ChatRoomMemberRole targetRoleSnapshot,
            ChatRoomMember bannedByMember,
            ChatRoomMemberRole bannedByRole,
            String reason
    ) {
        this.chatRoom = chatRoom;
        this.targetUser = targetUser;
        this.targetChatRoomMember = targetChatRoomMember;
        this.targetMemberCode = targetMemberCode;
        this.nicknameSnapshot = nicknameSnapshot;
        this.profileImageObjectKeySnapshot = normalizeObjectKey(
                profileImageObjectKeySnapshot
        );
        this.lastJoinedAtSnapshot = lastJoinedAtSnapshot;
        this.targetRoleSnapshot = targetRoleSnapshot;
        this.bannedByMember = bannedByMember;
        this.bannedByRole = bannedByRole;
        this.reason = reason;
        this.bannedAt = LocalDateTime.now();
    }

    public static OpenChatBan create(
            ChatRoom chatRoom,
            User targetUser,
            ChatRoomMember targetChatRoomMember,
            String targetMemberCode,
            String nicknameSnapshot,
            String profileImageObjectKeySnapshot,
            LocalDateTime lastJoinedAtSnapshot,
            ChatRoomMemberRole targetRoleSnapshot,
            ChatRoomMember bannedByMember,
            ChatRoomMemberRole bannedByRole,
            String reason
    ) {
        if (chatRoom == null
                || targetUser == null
                || targetChatRoomMember == null
                || bannedByMember == null
                || targetMemberCode == null
                || targetMemberCode.isBlank()
                || nicknameSnapshot == null
                || nicknameSnapshot.isBlank()
                || lastJoinedAtSnapshot == null
                || targetRoleSnapshot == null
                || bannedByRole == null
                || reason == null
                || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "OPEN 채팅 차단 이력 생성 값이 올바르지 않습니다."
            );
        }
        return new OpenChatBan(
                chatRoom,
                targetUser,
                targetChatRoomMember,
                targetMemberCode,
                nicknameSnapshot,
                profileImageObjectKeySnapshot,
                lastJoinedAtSnapshot,
                targetRoleSnapshot,
                bannedByMember,
                bannedByRole,
                reason
        );
    }

    public void release(ChatRoomMember releasedByMember) {
        if (this.releasedAt != null) {
            return;
        }
        this.releasedByMember = releasedByMember;
        this.releasedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.releasedAt == null;
    }

    private static String normalizeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return objectKey.trim();
    }
}
