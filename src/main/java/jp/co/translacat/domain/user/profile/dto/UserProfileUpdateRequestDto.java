package jp.co.translacat.domain.user.profile.dto;

public record UserProfileUpdateRequestDto(
        String nickname,
        String profileImageUrl,
        String bio
) {
}