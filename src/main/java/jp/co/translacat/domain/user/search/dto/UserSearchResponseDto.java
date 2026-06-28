package jp.co.translacat.domain.user.search.dto;

import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.search.enums.UserSearchFriendStatus;

public record UserSearchResponseDto(
        Long userId,
        String publicId,
        String nickname,
        String profileImageUrl,
        UserSearchFriendStatus friendStatus
) {

    public static UserSearchResponseDto of(
            UserSummaryProfileResponseDto profile,
            UserSearchFriendStatus friendStatus
    ) {
        return new UserSearchResponseDto(
                profile.userId(),
                profile.publicId(),
                profile.nickname(),
                profile.profileImageUrl(),
                friendStatus
        );
    }
}
