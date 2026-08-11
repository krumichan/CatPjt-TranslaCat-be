package jp.co.translacat.domain.chat.ai.dto.response;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;

import java.time.LocalDateTime;

public record ChatAiSafeProfileResponseDto(
        Long aiMemberId,
        String nickname,
        String profileImageUrl,
        String profileBackgroundImageUrl,
        String bio,
        String originalLanguageCode,
        boolean active,
        @ChatUtcTimestamp LocalDateTime joinedAt
) {
    public static ChatAiSafeProfileResponseDto from(
            ChatRoomAiMember aiMember,
            String profileImageUrl,
            String profileBackgroundImageUrl
    ) {
        return new ChatAiSafeProfileResponseDto(
                aiMember.getId(),
                aiMember.getAiAgent().getNickname(),
                profileImageUrl,
                profileBackgroundImageUrl,
                aiMember.getAiAgent().getBio(),
                aiMember.getAiAgent().getOriginalLanguageCode(),
                aiMember.isActive(),
                aiMember.getJoinedAt()
        );
    }
}
