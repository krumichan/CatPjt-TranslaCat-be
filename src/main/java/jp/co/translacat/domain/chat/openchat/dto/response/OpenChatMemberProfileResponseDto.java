package jp.co.translacat.domain.chat.openchat.dto.response;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

import java.time.LocalDateTime;

public record OpenChatMemberProfileResponseDto(
        Long openChatMemberId,
        String memberCode,
        String nickname,
        String profileImageUrl,
        ChatRoomMemberRole role,
        boolean active,
        Boolean online,
        @ChatUtcTimestamp LocalDateTime joinedAt
) {

    public OpenChatMemberProfileResponseDto(
            Long openChatMemberId,
            String memberCode,
            String nickname,
            String profileImageUrl,
            ChatRoomMemberRole role,
            boolean active,
            LocalDateTime joinedAt
    ) {
        this(
                openChatMemberId,
                memberCode,
                nickname,
                profileImageUrl,
                role,
                active,
                null,
                joinedAt
        );
    }

    public OpenChatMemberProfileResponseDto(
            Long openChatMemberId,
            String memberCode,
            String nickname,
            String profileImageUrl,
            ChatRoomMemberRole role,
            LocalDateTime joinedAt
    ) {
        this(
                openChatMemberId,
                memberCode,
                nickname,
                profileImageUrl,
                role,
                true,
                null,
                joinedAt
        );
    }
}
