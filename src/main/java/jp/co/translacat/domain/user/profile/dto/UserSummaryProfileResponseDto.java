package jp.co.translacat.domain.user.profile.dto;

import jp.co.translacat.domain.user.profile.entity.UserProfile;

public record UserSummaryProfileResponseDto(
        Long userId,
        String publicId,
        String nickname,
        String profileImageUrl,
        String profileBackgroundImageUrl,
        String bio
) {

    /**
     * 프로필 배경 이미지와 상태 메시지가 추가되기 전의
     * 기존 4개 인자 생성자와의 호환성을 유지한다.
     *
     * 기존 테스트 및 호출부에서는 새 필드가 null로 설정된다.
     */
    public UserSummaryProfileResponseDto(
            Long userId,
            String publicId,
            String nickname,
            String profileImageUrl
    ) {
        this(
                userId,
                publicId,
                nickname,
                profileImageUrl,
                null,
                null
        );
    }

    public static UserSummaryProfileResponseDto from(
            UserProfile userProfile,
            String profileImageUrl,
            String profileBackgroundImageUrl
    ) {
        return new UserSummaryProfileResponseDto(
                userProfile.getUser().getId(),
                userProfile.getUser().getPublicId(),
                userProfile.getNickname(),
                profileImageUrl,
                profileBackgroundImageUrl,
                userProfile.getBio()
        );
    }
}
