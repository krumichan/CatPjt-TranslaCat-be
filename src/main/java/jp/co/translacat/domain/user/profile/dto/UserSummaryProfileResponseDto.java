package jp.co.translacat.domain.user.profile.dto;

import jp.co.translacat.domain.user.profile.entity.UserProfile;

public record UserSummaryProfileResponseDto(
        Long userId,
        String publicId,
        String nickname,
        String profileImageUrl,
        String bio
) {

    /**
     * 기존 4개 인자 생성자를 사용하는 테스트나 호출부의 호환성을 유지한다.
     */
    public UserSummaryProfileResponseDto(
            Long userId,
            String publicId,
            String nickname,
            String profileImageUrl
    ) {
        this(userId, publicId, nickname, profileImageUrl, null);
    }

    public static UserSummaryProfileResponseDto from(
            UserProfile userProfile,
            String profileImageUrl
    ) {
        return new UserSummaryProfileResponseDto(
                userProfile.getUser().getId(),
                userProfile.getUser().getPublicId(),
                userProfile.getNickname(),
                profileImageUrl,
                userProfile.getBio()
        );
    }
}
