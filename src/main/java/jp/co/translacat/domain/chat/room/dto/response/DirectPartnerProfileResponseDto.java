package jp.co.translacat.domain.chat.room.dto.response;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.storage.service.UserProfileImageUrlResolver;

public record DirectPartnerProfileResponseDto(
        Long userId,
        String publicId,
        String displayName,
        String profileImageUrl,
        String profileBackgroundImageUrl,
        String bio
) {

    public static DirectPartnerProfileResponseDto from(
            User user,
            UserProfile userProfile,
            UserProfileImageUrlResolver imageUrlResolver
    ) {
        if (user == null) {
            return null;
        }

        return new DirectPartnerProfileResponseDto(
                user.getId(),
                user.getPublicId(),
                resolveDisplayName(user, userProfile),
                resolveProfileImageUrl(userProfile, imageUrlResolver),
                resolveProfileBackgroundImageUrl(userProfile, imageUrlResolver),
                resolveBio(userProfile)
        );
    }

    private static String resolveDisplayName(
            User user,
            UserProfile userProfile
    ) {
        if (userProfile != null && hasText(userProfile.getNickname())) {
            return userProfile.getNickname();
        }

        if (hasText(user.getUsername())) {
            return user.getUsername();
        }

        return user.getPublicId();
    }

    private static String resolveProfileImageUrl(
            UserProfile userProfile,
            UserProfileImageUrlResolver imageUrlResolver
    ) {
        if (userProfile == null || imageUrlResolver == null) {
            return null;
        }

        return imageUrlResolver.resolveProfileImageUrl(userProfile);
    }

    private static String resolveProfileBackgroundImageUrl(
            UserProfile userProfile,
            UserProfileImageUrlResolver imageUrlResolver
    ) {
        if (userProfile == null || imageUrlResolver == null) {
            return null;
        }

        return imageUrlResolver.resolveProfileBackgroundImageUrl(userProfile);
    }

    private static String resolveBio(UserProfile userProfile) {
        if (userProfile == null || !hasText(userProfile.getBio())) {
            return null;
        }

        return userProfile.getBio();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
