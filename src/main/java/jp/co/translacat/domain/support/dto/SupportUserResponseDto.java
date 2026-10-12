package jp.co.translacat.domain.support.dto;

import jp.co.translacat.domain.user.entity.User;

public record SupportUserResponseDto(Long id, String name, String email, String publicId) {

    public static SupportUserResponseDto from(User user) {
        return new SupportUserResponseDto(user.getId(), displayName(user), user.getEmail(), user.getPublicId());
    }

    public static String displayName(User user) {
        // 이름이 없는 계정도 공개 식별자로 표시하며 이메일을 화면 이름으로 노출하지 않는다.
        String name = user.getUsername();
        return name == null || name.isBlank() ? user.getPublicId() : name;
    }
}
