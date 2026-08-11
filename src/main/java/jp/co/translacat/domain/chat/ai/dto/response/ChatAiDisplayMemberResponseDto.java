package jp.co.translacat.domain.chat.ai.dto.response;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

import java.time.LocalDateTime;

public record ChatAiDisplayMemberResponseDto(
        Long aiMemberId,
        String nickname,
        String profileImageUrl,
        ChatRoomMemberRole role,
        boolean active,
        @ChatUtcTimestamp LocalDateTime joinedAt
) {
    public static ChatAiDisplayMemberResponseDto from(
            ChatRoomAiMember aiMember,
            String profileImageUrl
    ) {
        return new ChatAiDisplayMemberResponseDto(
                aiMember.getId(),
                aiMember.getAiAgent().getNickname(),
                profileImageUrl,
                ChatRoomMemberRole.MEMBER,
                aiMember.isActive(),
                aiMember.getJoinedAt()
        );
    }
}
