package jp.co.translacat.domain.chat.openchat.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "open_chat_member_profile",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_open_chat_member_profile_member",
                        columnNames = "chat_room_member_id"
                ),
                @UniqueConstraint(
                        name = "uk_open_chat_member_profile_code",
                        columnNames = "member_code"
                )
        },
        indexes = {
                @Index(
                        name = "idx_open_chat_member_profile_nickname",
                        columnList = "nickname"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpenChatMemberProfile extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_member_id", nullable = false)
    private ChatRoomMember chatRoomMember;

    @Column(name = "member_code", nullable = false, length = 20)
    private String memberCode;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_object_key", length = 500)
    private String profileImageObjectKey;

    private OpenChatMemberProfile(
            ChatRoomMember chatRoomMember,
            String memberCode,
            String nickname,
            String profileImageObjectKey
    ) {
        if (chatRoomMember == null
                || chatRoomMember.getChatRoom() == null
                || chatRoomMember.getChatRoom().getRoomType()
                != ChatRoomType.OPEN) {
            throw new IllegalArgumentException(
                    "OPEN 채팅방 멤버만 OPEN 프로필을 생성할 수 있습니다."
            );
        }
        this.chatRoomMember = chatRoomMember;
        this.memberCode = memberCode;
        this.nickname = nickname;
        this.profileImageObjectKey = normalizeObjectKey(
                profileImageObjectKey
        );
    }

    public static OpenChatMemberProfile create(
            ChatRoomMember chatRoomMember,
            String memberCode,
            String nickname,
            String profileImageObjectKey
    ) {
        return new OpenChatMemberProfile(
                chatRoomMember,
                memberCode,
                nickname,
                profileImageObjectKey
        );
    }

    public void update(
            String nickname,
            String profileImageObjectKey
    ) {
        this.nickname = nickname;
        this.profileImageObjectKey = normalizeObjectKey(
                profileImageObjectKey
        );
    }

    private static String normalizeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return objectKey.trim();
    }
}
