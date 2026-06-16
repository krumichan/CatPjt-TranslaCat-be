package jp.co.translacat.domain.user.dto;

import jp.co.translacat.domain.user.enums.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserLoginResponseDto {
    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpiresIn;
    private Role role;
    private String publicId;
}
