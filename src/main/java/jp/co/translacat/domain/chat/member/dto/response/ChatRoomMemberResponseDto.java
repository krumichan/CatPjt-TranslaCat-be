package jp.co.translacat.domain.chat.member.dto.response;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;

import java.time.LocalDateTime;

public record ChatRoomMemberResponseDto(
        Long id,
        Long chatRoomId,
        Long userId,
        String publicId,
        String displayName,
        String profileImageUrl,
        ChatRoomMemberRole role,
        boolean active,
        Boolean online,
        LocalDateTime joinedAt,
        LocalDateTime leftAt
) {

    /**
     * Presence 필드 추가 전 canonical 10개 인자 생성자 호환용.
     */
    public ChatRoomMemberResponseDto(
            Long id,
            Long chatRoomId,
            Long userId,
            String publicId,
            String displayName,
            String profileImageUrl,
            ChatRoomMemberRole role,
            boolean active,
            LocalDateTime joinedAt,
            LocalDateTime leftAt
    ) {
        this(
                id,
                chatRoomId,
                userId,
                publicId,
                displayName,
                profileImageUrl,
                role,
                active,
                null,
                joinedAt,
                leftAt
        );
    }

    /**
     * 기존 테스트 및 호출부의 9개 인자 생성자 호환용.
     * 기존 email 값은 방 메뉴 응답에서 더 이상 노출하지 않는다.
     */
    public ChatRoomMemberResponseDto(
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
        this(
                id,
                chatRoomId,
                userId,
                null,
                name,
                null,
                role,
                active,
                null,
                joinedAt,
                leftAt
        );
    }

    public static ChatRoomMemberResponseDto from(
            ChatRoomMember chatRoomMember,
            UserSummaryProfileResponseDto profile
    ) {
        return from(chatRoomMember, profile, null);
    }

    public static ChatRoomMemberResponseDto from(
            ChatRoomMember chatRoomMember,
            UserSummaryProfileResponseDto profile,
            Boolean online
    ) {
        return new ChatRoomMemberResponseDto(
                chatRoomMember.getId(),
                chatRoomMember.getChatRoom().getId(),
                chatRoomMember.getUser().getId(),
                profile.publicId(),
                profile.nickname(),
                profile.profileImageUrl(),
                chatRoomMember.getRole(),
                chatRoomMember.isActive(),
                online,
                chatRoomMember.getJoinedAt(),
                chatRoomMember.getLeftAt()
        );
    }

    /**
     * 기존 직접 호출부 호환용.
     */
    public static ChatRoomMemberResponseDto from(
            ChatRoomMember chatRoomMember
    ) {
        return new ChatRoomMemberResponseDto(
                chatRoomMember.getId(),
                chatRoomMember.getChatRoom().getId(),
                chatRoomMember.getUser().getId(),
                chatRoomMember.getUser().getPublicId(),
                chatRoomMember.getUser().getUsername(),
                null,
                chatRoomMember.getRole(),
                chatRoomMember.isActive(),
                null,
                chatRoomMember.getJoinedAt(),
                chatRoomMember.getLeftAt()
        );
    }
}
