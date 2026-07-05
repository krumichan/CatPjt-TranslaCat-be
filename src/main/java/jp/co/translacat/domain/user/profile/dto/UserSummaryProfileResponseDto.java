package jp.co.translacat.domain.user.profile.dto;

import jp.co.translacat.domain.user.profile.entity.UserProfile;

public record UserSummaryProfileResponseDto(
        Long userId,
        String publicId,
        String nickname,
        String profileImageUrl
) {

    public static UserSummaryProfileResponseDto from(
            UserProfile userProfile,
            String profileImageUrl
    ) {
        return new UserSummaryProfileResponseDto(
                userProfile.getUser().getId(),
                userProfile.getUser().getPublicId(),
                userProfile.getNickname(),
                profileImageUrl
        );
    }
}
