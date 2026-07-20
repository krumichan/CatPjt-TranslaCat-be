package jp.co.translacat.domain.chat.member.dto.response;

import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.search.enums.UserSearchFriendStatus;

public record ChatRoomMemberProfileResponseDto(
        Long userId,
        String publicId,
        String displayName,
        String profileImageUrl,
        String profileBackgroundImageUrl,
        String bio,
        UserSearchFriendStatus friendStatus
) {

    public static ChatRoomMemberProfileResponseDto of(
            UserSummaryProfileResponseDto profile,
            UserSearchFriendStatus friendStatus
    ) {
        return new ChatRoomMemberProfileResponseDto(
                profile.userId(),
                profile.publicId(),
                profile.nickname(),
                profile.profileImageUrl(),
                profile.profileBackgroundImageUrl(),
                profile.bio(),
                friendStatus
        );
    }
}
