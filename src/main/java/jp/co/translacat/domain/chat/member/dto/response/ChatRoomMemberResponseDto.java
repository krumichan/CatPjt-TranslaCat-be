package jp.co.translacat.domain.chat.member.dto.response;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;

import java.time.LocalDateTime;

public record ChatRoomMemberResponseDto(
        Long id,
        Long chatRoomId,
        Long userId,
        String name,
        String email,
        ChatRoomMemberRole role,
        boolean active,
        LocalDateTime joinedAt,
        LocalDateTime leftAt
) {

    public static ChatRoomMemberResponseDto from(ChatRoomMember chatRoomMember) {
        return new ChatRoomMemberResponseDto(
                chatRoomMember.getId(),
                chatRoomMember.getChatRoom().getId(),
                chatRoomMember.getUser().getId(),
                chatRoomMember.getUser().getUsername(),
                chatRoomMember.getUser().getEmail(),
                chatRoomMember.getRole(),
                chatRoomMember.isActive(),
                chatRoomMember.getJoinedAt(),
                chatRoomMember.getLeftAt()
        );
    }
}