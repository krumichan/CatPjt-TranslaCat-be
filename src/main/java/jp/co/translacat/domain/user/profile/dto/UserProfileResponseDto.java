package jp.co.translacat.domain.user.profile.dto;

import jp.co.translacat.domain.user.profile.entity.UserProfile;

import java.time.LocalDateTime;

public record UserProfileResponseDto(
        Long userId,
        String publicId,
        String nickname,
        String profileImageUrl,
        String profileBackgroundImageUrl,
        String bio,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserProfileResponseDto from(
            UserProfile userProfile,
            String profileImageUrl,
            String profileBackgroundImageUrl
    ) {
        return new UserProfileResponseDto(
                userProfile.getUser().getId(),
                userProfile.getUser().getPublicId(),
                userProfile.getNickname(),
                profileImageUrl,
                profileBackgroundImageUrl,
                userProfile.getBio(),
                userProfile.getCreatedAt(),
                userProfile.getUpdatedAt()
        );
    }
}
