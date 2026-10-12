package jp.co.translacat.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UserLoginRequestDto {
    @NotBlank(message = "The 'email' does not allow empty values.")
    @Schema(description = "user email", example = "tester@gmail.com")
    private String email;

    @NotBlank(message = "The 'password' field does not allow null or empty values.")
    @Schema(description = "user password", example = "tester")
    private String password;
}
