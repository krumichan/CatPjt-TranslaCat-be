package jp.co.translacat.domain.chat.ai.dto.response;

import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;

import java.time.LocalDateTime;

public record ChatAiMemberResponseDto(
        Long aiMemberId,
        Long aiAgentId,
        Long chatRoomId,
        String nickname,
        String profileImageUrl,
        String profileBackgroundImageUrl,
        String bio,
        String originalLanguageCode,
        String personaPrompt,
        boolean active,
        LocalDateTime joinedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ChatAiMemberResponseDto from(
            ChatRoomAiMember aiMember,
            String profileImageUrl,
            String profileBackgroundImageUrl
    ) {
        return new ChatAiMemberResponseDto(
                aiMember.getId(),
                aiMember.getAiAgent().getId(),
                aiMember.getChatRoom().getId(),
                aiMember.getAiAgent().getNickname(),
                profileImageUrl,
                profileBackgroundImageUrl,
                aiMember.getAiAgent().getBio(),
                aiMember.getAiAgent().getOriginalLanguageCode(),
                aiMember.getAiAgent().getPersonaPrompt(),
                aiMember.isActive(),
                aiMember.getJoinedAt(),
                aiMember.getCreatedAt(),
                aiMember.getUpdatedAt()
        );
    }
}
