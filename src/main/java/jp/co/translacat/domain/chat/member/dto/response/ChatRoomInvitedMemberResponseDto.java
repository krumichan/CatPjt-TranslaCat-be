package jp.co.translacat.domain.chat.member.dto.response;

import jp.co.translacat.domain.chat.common.json.ChatUtcTimestamp;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;

import java.time.LocalDateTime;

public record ChatRoomInvitedMemberResponseDto(
        Long userId,
        String publicId,
        String displayName,
        String profileImageUrl,
        @ChatUtcTimestamp LocalDateTime joinedAt
) {

    public static ChatRoomInvitedMemberResponseDto of(
            ChatRoomMember member,
            UserSummaryProfileResponseDto profile
    ) {
        return new ChatRoomInvitedMemberResponseDto(
                member.getUser().getId(),
                profile.publicId(),
                profile.nickname(),
                profile.profileImageUrl(),
                member.getJoinedAt()
        );
    }
}
